package io.cloudtrust.keycloak.test.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.test.util.JsonToolbox;
import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.representations.idm.ComponentExportRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.userprofile.DeclarativeUserProfileProvider;
import org.keycloak.userprofile.DeclarativeUserProfileProviderFactory;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.config.UPConfigUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AbstractRealmConfig implements RealmConfig {
    private static final Logger LOG = Logger.getLogger(AbstractRealmConfig.class);

    private final String filename;
    private String[] singleValuedAttributes = null;
    private String[] multiValuedAttributes = null;
    private List<RequiredActionProviderRepresentation> requiredActions = null;

    protected AbstractRealmConfig() {
        this.filename = null;
    }

    protected AbstractRealmConfig(String filename) {
        this.filename = filename;
    }

    public void setSingleValuedAttributes(String... attributes) {
        this.singleValuedAttributes = attributes;
    }

    public void setMultiValuedAttributes(String... attributes) {
        this.multiValuedAttributes = attributes;
    }

    public void setEmptyRequiredActions() {
        this.requiredActions = new ArrayList<>(); // Let this list be updatable
    }

    public void setRequiredActions(List<RequiredActionProviderRepresentation> requiredActions) {
        this.requiredActions = requiredActions;
    }

    protected void customizeRealm(RealmRepresentation realmRepresentation) {
        // Default implementation does nothing. Subclasses may override to customize the realm.
    }

    @Override
    public RealmConfigBuilder configure(RealmConfigBuilder realmConfigBuilder) {
        try {
            if (filename != null) {
                var inputStream = getClass().getResourceAsStream(filename);
                var realmRepresentation = new ObjectMapper().readValue(inputStream, RealmRepresentation.class);
                realmConfigBuilder = RealmConfigBuilder.update(realmRepresentation);
            }
        } catch (IOException e) {
            LOG.error("Can't initialize realm config", e);
        }
        var realmRep = realmConfigBuilder.build();
        declareUserProfile(realmRep);
        if (requiredActions != null) {
            realmRep.setRequiredActions(requiredActions);
        }
        customizeRealm(realmRep);
        if (LOG.isDebugEnabled()) {
            LOG.debug(JsonToolbox.toString(realmRep));
        }
        return realmConfigBuilder;
    }

    private void declareUserProfile(RealmRepresentation realm) {
        if ((singleValuedAttributes == null || singleValuedAttributes.length == 0) &&
                (multiValuedAttributes == null || multiValuedAttributes.length == 0)) {
            return;
        }
        var upConfig = UPConfigUtils.parseSystemDefaultConfig();
        if (singleValuedAttributes != null) {
            for (var name : singleValuedAttributes) {
                upConfig.addOrReplaceAttribute(createAttribute(name, false));
            }
        }
        if (multiValuedAttributes != null) {
            for (var name : multiValuedAttributes) {
                upConfig.addOrReplaceAttribute(createAttribute(name, true));
            }
        }

        var component = new ComponentExportRepresentation();
        component.setProviderId(DeclarativeUserProfileProviderFactory.ID);
        component.getConfig().putSingle(DeclarativeUserProfileProvider.UP_COMPONENT_CONFIG_KEY, JsonToolbox.toString(upConfig));

        var components = realm.getComponents();
        if (components == null) {
            components = new MultivaluedHashMap<>();
            realm.setComponents(components);
        }
        components.add(UserProfileProvider.class.getName(), component);
    }

    /**
     * createAttribute creates a UPAttribute for the specified attribute name.
     *
     * @param name        The name of the attribute
     * @param multivalued Tells if attribute should be multivalued or not
     * @return The created UPAttribute
     */
    private UPAttribute createAttribute(String name, boolean multivalued) {
        var attribute = new UPAttribute(name, multivalued,
                new UPAttributePermissions(
                        Set.of(UPConfigUtils.ROLE_ADMIN, UPConfigUtils.ROLE_USER),
                        Set.of(UPConfigUtils.ROLE_ADMIN, UPConfigUtils.ROLE_USER)));
        attribute.setDisplayName(name);
        return attribute;
    }
}