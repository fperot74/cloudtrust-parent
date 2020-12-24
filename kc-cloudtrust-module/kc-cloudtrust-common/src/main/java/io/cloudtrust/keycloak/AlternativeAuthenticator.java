package io.cloudtrust.keycloak;

import org.keycloak.credential.CredentialModel;
import org.keycloak.models.AuthenticationExecutionModel;

/**
 * Inspired by AuthenticationSelectionOption in Keycloak 8.0.2
 * @author fpe
 *
 */
public class AlternativeAuthenticator {
    private AuthenticationExecutionModel authExec;
    private CredentialModel credential;

    public AlternativeAuthenticator(AuthenticationExecutionModel authExec, CredentialModel credential) {
        this.authExec = authExec;
        this.credential = credential;
    }

    public String getId() {
        if (getCredentialId() == null) {
            return getAuthExecId() + "|";
        }
        return getAuthExecId() + "|" + getCredentialId();
    }

    public String getAuthExecId() {
        return this.authExec.getId();
    }

    public String getAuthExecName() {
        return this.authExec.getAuthenticator();
    }

    public boolean showCredentialType() {
        return false;
    }

    public String getCredentialId() {
        return this.credential.getId();
    }

    public String getCredentialName() {
        return this.credential.getUserLabel();
    }
}
