package io.cloudtrust.keycloak;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationSelectionOption;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialTypeMetadata;
import org.keycloak.credential.CredentialTypeMetadataContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;

/**
 * While upgrading from KC8 to KC14, we noticed changes in the way login pages are managed.
 * In KC14, selected credential is not stored the same way it was in KC8 and we want to
 * keep the previous version behavior. Using this toolbox allows to keep the previous way
 * of choosing alternative credentials
 *
 * @author fpe
 *
 */
public class FormUtils {
    private FormUtils() {
    }

    public static LoginFormsProvider getFormWithAuthenticators(AuthenticationFlowContext context, String selectedCredentialId) {
        return getFormWithAuthenticators(context, selectedCredentialId, u -> {});
    }

    public static LoginFormsProvider getFormWithAuthenticators(AuthenticationFlowContext context, String selectedCredentialId, Consumer<AlternativeAuthenticator> optionUpdater) {
        LoginFormsProvider form = context.form();

        Map<String, AuthenticationExecutionModel> authExecs = context.getAuthenticationSelections().stream()
                .collect(Collectors.toMap(AuthenticationSelectionOption::getDisplayName, AuthenticationSelectionOption::getAuthenticationExecution));
        final CredentialTypeMetadataContext ctmCtx = CredentialTypeMetadataContext.builder().user(context.getUser()).build(context.getSession());
        Map<String, AuthenticationExecutionModel> authTypes = context.getSession().getAllProviders(CredentialProvider.class).stream()
                .map(p -> {
                    CredentialTypeMetadata md = p.getCredentialTypeMetadata(ctmCtx);
                    if (md == null) {
                        return null;
                    }
                    AuthenticationExecutionModel authExec = authExecs.get(md.getDisplayName());
                    if (authExec == null) {
                        return null;
                    }
                    return Pair.of(p.getType(), authExec);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        List<AlternativeAuthenticator> credentials = context.getSession().userCredentialManager().getStoredCredentialsStream(context.getRealm(), context.getUser())
                .map(c -> {
                    AuthenticationExecutionModel authExec = authTypes.get(c.getType());
                    if (authExec == null) {
                        return null;
                    }
                    AlternativeAuthenticator ao = new AlternativeAuthenticator(authExec, c);
                    optionUpdater.accept(ao);
                    return ao;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        TrustIDContext ctx = new TrustIDContext();
        ctx.setCredentialId(selectedCredentialId);
        ctx.setCredentials(credentials);

        form.setAttribute("trustIdContext", ctx);

        return form;
    }
}
