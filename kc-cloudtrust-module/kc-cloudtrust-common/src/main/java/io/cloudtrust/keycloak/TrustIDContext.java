package io.cloudtrust.keycloak;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TrustIDContext {
    private String credentialId;
    private List<AlternativeAuthenticator> credentials;

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public List<AlternativeAuthenticator> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<AlternativeAuthenticator> credentials) {
        this.credentials = credentials;
    }

    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch(Exception e) {
            return super.toString();
        }
    }
}
