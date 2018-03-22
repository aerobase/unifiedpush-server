package org.jboss.aerogear.unifiedpush.auth;

import java.io.InputStream;

import org.jboss.aerogear.unifiedpush.service.impl.spring.OAuth2Configuration;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;

public class CustomKeycloakDeploymentBuilder extends KeycloakDeploymentBuilder {

	public CustomKeycloakDeploymentBuilder() {
		deployment = new CustomKeycloakDeployment();
	}

	/**
	 * @param isProxy
	 *            Update realm name according to proxy subdmain or default
	 *            system realm.</br>
	 *
	 *            <li>false, use system default realm. e.g:
	 *            OAuth2Configuration.getStaticUpsRealm()
	 *
	 *            <li>true, use subdomain name as realm name e.g:
	 *            <b>portal</b>.aerobase.io ,<b>test-mail-org</b>.aerobase.io
	 *
	 * @param inputStream
	 *            Realm input stream
	 * @param proxyRealmName
	 *            proxy subdomain
	 */
	public static CustomKeycloakDeployment build(InputStream inputStream, boolean isProxy, String proxyRealmName, String clientName) {
		AdapterConfig adapterConfig = loadAdapterConfig(inputStream);

		// Override realm attributes from system properties if exists.
		String upsRealmName = OAuth2Configuration.getStaticUpsRealm();
		String upsAuthServer = OAuth2Configuration.getStaticOAuth2Url();

		// This override properties from static WEB-INF json files.
		if (!isProxy) {
			adapterConfig.setRealm(upsRealmName);
		} else {
			adapterConfig.setResource(clientName);
			adapterConfig.setRealm(proxyRealmName);
		}

		adapterConfig.setAuthServerUrl(upsAuthServer);

		return (CustomKeycloakDeployment) new CustomKeycloakDeploymentBuilder().internalBuild(adapterConfig);
	}

}
