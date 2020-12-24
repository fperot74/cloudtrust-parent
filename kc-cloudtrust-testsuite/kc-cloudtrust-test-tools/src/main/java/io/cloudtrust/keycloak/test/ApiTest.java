package io.cloudtrust.keycloak.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.resource.RealmResourceProviderFactory;
import org.keycloak.test.FluentTestsHelper;
import org.keycloak.testsuite.arquillian.KcArquillian;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(KcArquillian.class)
@RunAsClient
public abstract class ApiTest {
    protected static final String KEYCLOAK_URL = getKeycloakUrl();

    protected ObjectMapper mapper = new ObjectMapper();
    protected RealmResource testRealm;
    protected Keycloak keycloak;
    protected String token;

    private static String getKeycloakUrl() {
        String url = FluentTestsHelper.DEFAULT_KEYCLOAK_URL;
        try {
            URI uri = new URI(FluentTestsHelper.DEFAULT_KEYCLOAK_URL);
            url = url.replace(String.valueOf(uri.getPort()), System.getProperty("auth.server.http.port", "8080"));
        } catch (Exception e) {
            // Ignore
        }
        return url;
    }

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "run-on-server-classes.war")
                .addPackages(true, "io.cloudtrust.keycloak")
                .addAsManifestResource(new File("src/test/resources", "manifest.xml"))
                .addAsServiceProvider(RealmResourceProviderFactory.class, RealmResourceProviderFactory.class);
    }

    @Before
    public void createTestRealm() throws IOException {
        keycloak = Keycloak.getInstance(KEYCLOAK_URL, "master", "admin", "admin", "admin-cli");
        token = keycloak.tokenManager().getAccessTokenString();
        testRealm = importTestRealm(keycloak);
        setupTestRealm();
    }

    protected void setupTestRealm() throws IOException {
    }

    @After
    public void deleteTestRealm() {
        testRealm.remove();
    }

    private RealmResource importTestRealm(Keycloak keycloak) throws IOException {
        RealmRepresentation realmRepresentation = mapper.readValue(
                ApiTest.class.getResourceAsStream("/testrealm.json"), RealmRepresentation.class);
        keycloak.realms().create(realmRepresentation);
        return keycloak.realm("test");
    }

    protected <T> T queryApi(Class<T> clazz, String method, String apiPath) throws IOException, URISyntaxException {
        return mapper.readValue(callApi(method, apiPath, new ArrayList<>()), clazz);
    }

    protected <T> T queryApi(Class<T> clazz, String method, String apiPath, List<NameValuePair> nvps) throws IOException, URISyntaxException {
        return mapper.readValue(callApi(method, apiPath, nvps), clazz);
    }

    protected String callApi(String apiPath) throws IOException, URISyntaxException {
        return callApi("GET", apiPath, new ArrayList<>());
    }

    protected String callApi(String method, String apiPath) throws IOException, URISyntaxException {
        return callApi(method, apiPath, new ArrayList<>());
    }

    protected String callApi(String method, String apiPath, List<NameValuePair> nvps) throws IOException, URISyntaxException {
        return callApi(method, apiPath, nvps, null);
    }

    protected String callApiJSON(String method, String apiPath, Object jsonable) throws IOException, URISyntaxException {
        return callApiJSON(method, apiPath, new ArrayList<>(), jsonable);
    }

    protected String callApiJSON(String method, String apiPath, List<NameValuePair> nvps, Object jsonable) throws IOException, URISyntaxException {
        String json = new ObjectMapper().writeValueAsString(jsonable);
        StringEntity requestEntity = new StringEntity(json, ContentType.APPLICATION_JSON);
        return callApi(method, apiPath, nvps, requestEntity);
    }

    protected String callApi(String method, String apiPath, List<NameValuePair> nvps, HttpEntity entity) throws IOException, URISyntaxException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            URIBuilder uriBuilder = new URIBuilder(KEYCLOAK_URL + apiPath);
            uriBuilder.addParameters(nvps);
            HttpRequestBase get = createHttpRequest(method, uriBuilder.build(), entity);
            get.addHeader("Authorization", "Bearer " + token);

            HttpResponse response = client.execute(get);
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new HttpResponseException(response.getStatusLine().getStatusCode(), "call failed: " + response.getStatusLine().getStatusCode());
            }
            if (response.getEntity() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                    return reader.lines().collect(Collectors.joining());
                }
            }
            return null;
        }
    }

    private HttpRequestBase createHttpRequest(String method, URI uri, HttpEntity entity) throws HttpResponseException {
        switch (method) {
            case "GET":
                return new HttpGet(uri);
            case "PUT":
                return addBodyToHttpRequest(new HttpPut(uri), entity);
            default:
                throw new HttpResponseException(405, "Unsupported method " + method);
        }
    }

    private HttpRequestBase addBodyToHttpRequest(HttpEntityEnclosingRequestBase httpRequest, HttpEntity entity) {
        if (entity != null) {
            httpRequest.setEntity(entity);
        }
        return httpRequest;
    }
}
