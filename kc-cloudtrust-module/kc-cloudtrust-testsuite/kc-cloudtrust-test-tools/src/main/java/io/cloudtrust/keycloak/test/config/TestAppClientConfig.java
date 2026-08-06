package io.cloudtrust.keycloak.test.config;

import org.keycloak.testframework.realm.ClientConfig;
import org.keycloak.testframework.realm.ClientConfigBuilder;

public class TestAppClientConfig implements ClientConfig {
    @Override
    public ClientConfigBuilder configure(ClientConfigBuilder client) {
        return client.clientId("test-app").redirectUris("http://localhost:8080/*");
    }
}