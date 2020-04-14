package mops.gruppen2.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.web.client.RestTemplate;

/**
 * WORKAROUND for https://issues.redhat.com/browse/KEYCLOAK-11282
 * Bean should move into {@link SecurityConfig} once Bug has been resolved
 */

@Configuration
@KeycloakConfiguration
public class KeycloakConfig {

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("f73354fd-614e-4e24-b801-a8d0f4abf531")
    private String clientSecret;

    @Value("https://gruppenkeycloak.herokuapp.com/auth/realms/master/protocol/openid-connect/token")
    private String tokenUri;

    @Bean
    public KeycloakSpringBootConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    @Bean
    public RestTemplate serviceAccountRestTemplate() {
        ClientCredentialsResourceDetails resourceDetails = new ClientCredentialsResourceDetails();

        resourceDetails.setGrantType(OAuth2Constants.CLIENT_CREDENTIALS);
        resourceDetails.setAccessTokenUri(tokenUri);
        resourceDetails.setClientId(clientId);
        resourceDetails.setClientSecret(clientSecret);

        return new OAuth2RestTemplate(resourceDetails);
    }
}
