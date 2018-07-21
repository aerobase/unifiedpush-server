package org.jboss.aerogear.unifiedpush.auth;

import java.io.InputStream;

import org.jboss.aerogear.unifiedpush.service.impl.spring.OAuth2Configuration;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;

public class CustomKeycloakDeploymentBuilder extends KeycloakDeploymentBuilder {

	public CustomKeycloakDeploymentBuilder() {
		deployment = new CustomKeycloakDeployment();
	}

	public static CustomKeycloakDeployment build(InputStream inputStream, String realmName, String clientName) {
		AdapterConfig adapterConfig = loadAdapterConfig(inputStream);

		// Override realm attributes from system properties if exists.
		String upsAuthServer = OAuth2Configuration.getStaticOAuth2Url();

		// This override properties from static WEB-INF json files.
		adapterConfig.setResource(clientName);
		adapterConfig.setRealm(realmName);

		adapterConfig.setAuthServerUrl(upsAuthServer);

		return (CustomKeycloakDeployment) new CustomKeycloakDeploymentBuilder().internalBuild(adapterConfig);
	}

}
