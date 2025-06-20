package io.cloudtrust.keycloak.test.ctpages;

import io.cloudtrust.exception.CloudtrustRuntimeException;
import io.cloudtrust.keycloak.test.util.OAuthClient;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.keycloak.testframework.ui.page.AbstractPage;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.openqa.selenium.support.ui.ExpectedConditions.javaScriptThrowsNoExceptions;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public abstract class AbstractCtPage extends AbstractPage {
    private static final Logger log = Logger.getLogger(AbstractCtPage.class.getName());

    // Values used to name web pages when using the save feature
    private static final int INSTANCE_ID = new SecureRandom().nextInt() & 0xff;
    private static int saveIndex = 0;

    // This is used for navigation methods saveUrl() and restoreUrl()
    private final Deque<String> savedURLs = new ArrayDeque<>();

    public static final String PAGELOAD_TIMEOUT_PROP = "pageload.timeout";
    public static final Integer PAGELOAD_TIMEOUT_MILLIS = Integer.parseInt(System.getProperty(PAGELOAD_TIMEOUT_PROP, "10000"));

    protected OAuthClient oauthClient;

    public AbstractCtPage(WebDriver driver) {
        super(driver);
    }

    private static synchronized int nextSaveIndex() {
        return ++saveIndex;
    }

    public void assertCurrent() {
        String name = getClass().getSimpleName();
        Assertions.assertTrue(isCurrent(), "Expected " + name + " but was " + driver.getTitle() + " (" + driver.getCurrentUrl() + ")");
    }

    public boolean isCurrent() {
        return false;
    }

    public boolean isNotCurrent() {
        return !isCurrent();
    }

    public String getLoginFormUrl() {
        return this.oauthClient.getLoginFormUrl();
    }

    public void open() {
        throw new CloudtrustRuntimeException("open() not implemented");
    }

    /**
     * Navigate to a logout URL. Automatically confirm logout if necessary
     */
    public void openLogout() {
        openLogout(true);
    }

    /**
     * Navigate to a logout URL
     *
     * @param automaticallyConfirmLogout If a confirmation is required, tells if confirmation should be given automatically or not
     */
    public void openLogout(boolean automaticallyConfirmLogout) {
        driver.navigate().to(oauthClient.getLogoutFormUrl());
        if (automaticallyConfirmLogout) {
            WebElement elt = this.driver.findElement(By.id("kc-logout"));
            if (elt != null) {
                elt.click();
            }
        }
    }

    public OAuthClient getOAuthClient() {
        return this.oauthClient;
    }

    public void setOAuthClient(OAuthClient client) {
        this.oauthClient = client;
    }

    public void clickLink(WebElement element) {
        //waitUntilElement(element).is().clickable();

        // Safari sometimes thinks an element is not visible
        // even though it is. In this case we just move the cursor and click.
        if (driver instanceof SafariDriver && !element.isDisplayed()) {
            performOperationWithPageReload(() -> new Actions(driver).click(element).perform());
        } else {
            performOperationWithPageReload(element::click);
        }
    }

    protected void performOperationWithPageReload(Runnable operation) {
        operation.run();
        waitForPageToLoad();
    }

    protected void waitForPageToLoad() {
        if (this.driver instanceof HtmlUnitDriver) {
            return; // not needed
        }

        String currentUrl = null;

        // Ensure the URL is "stable", i.e. is not changing anymore; if it'd changing, some redirects are probably still in progress
        for (int maxRedirects = 4; maxRedirects > 0; maxRedirects--) {
            currentUrl = this.driver.getCurrentUrl();
            FluentWait<WebDriver> wait = new FluentWait<>(this.driver).withTimeout(Duration.ofMillis(250));
            try {
                wait.until(not(urlToBe(currentUrl)));
            } catch (TimeoutException e) {
                break; // URL has not changed recently - ok, the URL is stable and page is current
            }
            if (maxRedirects == 1) {
                log.warn("URL seems unstable! (Some redirect are probably still in progress)");
            }
        }

        WebDriverWait wait = new WebDriverWait(this.driver, Duration.ofMillis(PAGELOAD_TIMEOUT_MILLIS));
        ExpectedCondition<Boolean> waitCondition = null;

        // Different wait strategies for Admin and Account Consoles
        if (currentUrl.matches("^[^\\/]+:\\/\\/[^\\/]+\\/admin\\/.*$")) { // Admin Console
            // Checks if the document is ready and asks AngularJS, if present, whether there are any REST API requests in progress
            waitCondition = javaScriptThrowsNoExceptions(
                    "if (document.readyState !== 'complete' "
                            + "|| (typeof angular !== 'undefined' && angular.element(document.body).injector().get('$http').pendingRequests.length !== 0)) {"
                            + "throw \"Not ready\";"
                            + "}");
        } else if (currentUrl.matches("^[^\\/]+:\\/\\/[^\\/]+\\/realms\\/[^\\/]+\\/account\\/.*#/.+$")) {
            // check for new Account Console URL
            pause(2000); // TODO rework this temporary workaround once KEYCLOAK-11201 and/or KEYCLOAK-8181 are fixed
        }

        if (waitCondition != null) {
            try {
                wait.until(waitCondition);
            } catch (TimeoutException e) {
                log.warn("waitForPageToLoad time exceeded!");
            }
        }
    }

    /**
     * Wait during a max duration until an element is shown
     * @param by
     * @param maxDuration
     * @return
     */
    public WebElement waitForElement(By by, long maxDuration) {
        NoSuchElementException except = null;
        for(long limit = System.currentTimeMillis() + maxDuration; System.currentTimeMillis()<limit; ) {
            try {
                return driver.findElement(by);
            } catch (NoSuchElementException nsee) {
                except = nsee;
                pause(200);
            }
        }
        assert except != null;
        throw except;
    }

    public void pause(long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                log.fatal("Interrupted", ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    public String getTextFromElement(WebElement element) {
        String text = element.getText();
        if (driver instanceof SafariDriver) {
            try {
                // Safari on macOS doesn't comply with WebDriver specs yet again - getText() retrieves hidden text by CSS.
                text = element.findElement(By.xpath("./span[not(contains(@class,'ng-hide'))]")).getText();
            } catch (NoSuchElementException e) {
                // no op
            }
            return text.trim(); // Safari on macOS sometimes for no obvious reason surrounds the text with spaces
        }
        return text;
    }

    public static void setTextValue(WebElement input, String value) {
        input.click();
        input.clear();
        if (!StringUtils.isEmpty(value)) { // setting new input
            input.sendKeys(value);
        } else { // just clearing the input; input.clear() may not fire all JS events so we need to let the page know that something's changed
            input.sendKeys("1");
            input.sendKeys(Keys.BACK_SPACE);
        }
    }

    public String getCurrentUrl() {
        return this.driver.getCurrentUrl();
    }

    public String getPageSource() {
        return driver.getPageSource();
    }

    public String getPageTitle(WebDriver driver) {
        return driver.findElement(By.id("kc-page-title")).getText();
    }

    public boolean pageContains(String text) {
        return driver.getPageSource().contains(text);
    }

    public String getPageTitle() {
        try {
            return driver.findElement(By.id("kc-page-title")).getText();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * This method is provided for debug purpose only. You should not commit code using this method.
     *
     * @param comment
     */
    public void save(String comment) {
        try {
            File res = File.createTempFile("web-" + INSTANCE_ID + "-" + nextSaveIndex() + "-", ".html");
            log.info("Writing temporary file " + res.getAbsolutePath());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(res))) {
                writer.write("<!-- " + this.getCurrentUrl() + " -->\n");
                writer.write("<!-- " + comment + " -->\n");
                writer.write(this.driver.getPageSource());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Save the current URL
     */
    public void saveURL() {
        this.savedURLs.add(this.driver.getCurrentUrl());
    }

    public void restoreURL() {
        if (!this.savedURLs.isEmpty()) {
            String url = this.savedURLs.pop();
            log.info("Returning to " + url);
            this.driver.navigate().to(url);
        }
    }
}
