/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.rest.util;

import java.util.Enumeration;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.service.GenericVariantService;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;
import org.jboss.aerogear.unifiedpush.service.impl.spring.KeycloakServiceImpl;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.representations.AccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public final class BearerHelper {
	private static final Logger logger = LoggerFactory.getLogger(KeycloakServiceImpl.class);

	private static final String BEARER_SCHEME = "Bearer";
	private BearerHelper() {
	}

	public static Variant extractVariantFromBearerHeader(LoggedInUser account,
			GenericVariantService genericVariantService, HttpServletRequest request) {
		String clientId = extractClientId(request);
		if (StringUtils.isNotBlank(clientId)) {
			return genericVariantService.findVariantByKeycloakClientID(account, clientId);
		}

		return null;
	}

	public static String extractClientId(HttpServletRequest request) {
		String clientId = null;

		AccessToken token = getTokenDataFromBearer(request).orNull();
		if (token != null) {
			clientId = token.getIssuedFor();
		}

		return clientId;
	}

	public static Optional<AccessToken> getTokenDataFromBearer(org.keycloak.adapters.spi.HttpFacade.Request request) {
		return getTokenDataFromBearer(getBarearToken(request).orNull());
	}

	public static Optional<AccessToken> getTokenDataFromBearer(HttpServletRequest request) {
		return getTokenDataFromBearer(getBarearToken(request).orNull());
	}

	private static Optional<AccessToken> getTokenDataFromBearer(String tokenString) {
		if (tokenString != null) {
			try {
				JWSInput input = new JWSInput(tokenString);
				return Optional.of(input.readJsonContent(AccessToken.class));
			} catch (JWSInputException e) {
				logger.debug("could not parse token: ", e);
			}
		}

		return Optional.absent();
	}

	// Barear authentication allowed only using keycloack context
	public static Optional<String> getBarearToken(org.keycloak.adapters.spi.HttpFacade.Request request) {
		return getBarearToken(new Vector<String>(request.getHeaders("Authorization")).elements());
	}

	// Barear authentication allowed only using keycloack context
	public static Optional<String> getBarearToken(HttpServletRequest request) {
		return getBarearToken(request.getHeaders("Authorization"));
	}

	// Barear authentication allowed only using keycloack context
	private static Optional<String> getBarearToken(Enumeration<String> authHeaders) {
		if (authHeaders == null || !authHeaders.hasMoreElements()) {
			return Optional.absent();
		}

		String tokenString = null;
		while (authHeaders.hasMoreElements()) {
			String[] split = authHeaders.nextElement().trim().split("\\s+");
			if (split == null || split.length != 2)
				continue;
			if (!split[0].equalsIgnoreCase(BEARER_SCHEME))
				continue;
			tokenString = split[1];
		}

		if (tokenString == null) {
			return Optional.absent();
		}
		return Optional.of(tokenString);
	}

	public static String getRealmName(org.keycloak.adapters.spi.HttpFacade.Request request) {
		AccessToken accessToken = BearerHelper.getTokenDataFromBearer(request).orNull();
		if (accessToken != null) {
			String issuer = accessToken.getIssuer();

			if (StringUtils.isNoneEmpty(issuer)) {
				issuer = HttpRequestUtil.removeLastSlash(issuer);
				return HttpRequestUtil.getLastPart(issuer);
			}
		}

		return null;
	}

	public static String getClientName(org.keycloak.adapters.spi.HttpFacade.Request request) {
		AccessToken accessToken = BearerHelper.getTokenDataFromBearer(request).orNull();
		if (accessToken != null && accessToken.getAudience() != null && accessToken.getAudience().length > 0) {
			return accessToken.getAudience()[0];
		}

		return null;
	}

	// Barear authentication request
	public static boolean isBearerExists(HttpServletRequest request) {
		return BearerHelper.getBarearToken(request).isPresent();
	}
}
