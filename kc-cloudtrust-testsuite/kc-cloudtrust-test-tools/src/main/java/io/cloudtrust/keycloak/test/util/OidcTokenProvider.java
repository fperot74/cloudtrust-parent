package io.cloudtrust.keycloak.test.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jboss.logging.Logger;
import org.keycloak.util.BasicAuthHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OidcTokenProvider {
    private static final Logger LOG = Logger.getLogger(OidcTokenProvider.class);

    private final String keycloakURL;
    private final String oidcAuthPath;
    private final String basicAuth;

    public OidcTokenProvider(String keycloakURL, String oidcAuthPath, String clientId, String password) {
        this.keycloakURL = keycloakURL;
        this.oidcAuthPath = oidcAuthPath;
        this.basicAuth = BasicAuthHelper.createHeader(clientId, password);
    }

    public HttpResponse createOidcToken(String username, String password, String... paramPairs) throws IOException {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost httpPost = new HttpPost(keycloakURL + oidcAuthPath);
            httpPost.addHeader("Authorization", basicAuth);

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("grant_type", "password"));
            params.add(new BasicNameValuePair("scope", "openid"));
            params.add(new BasicNameValuePair("username", username));
            params.add(new BasicNameValuePair("password", password));
            if (paramPairs != null) {
                for (int i = 0; i < paramPairs.length - 1; i += 2) {
                    params.add(new BasicNameValuePair(paramPairs[i], paramPairs[i + 1]));
                }
            }
            httpPost.setEntity(new UrlEncodedFormEntity(params));

            // call the OIDC interface
            return httpClient.execute(httpPost);
        }
    }

    public String getAccessToken(String username, String password, String... paramPairs) throws IOException {
        HttpResponse response = createOidcToken(username, password, paramPairs);
        String responseBody = EntityUtils.toString(response.getEntity());
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = getValue(jsonNode, "access_token");
        if (accessToken == null) {
            LOG.warnf("Failed to retrieve an access token. Response was %s", responseBody);
        }
        return accessToken;
    }

    private static String getValue(JsonNode parentNode, String key) {
        JsonNode node = parentNode.get(key);
        return node == null ? null : node.asText();
    }
}
