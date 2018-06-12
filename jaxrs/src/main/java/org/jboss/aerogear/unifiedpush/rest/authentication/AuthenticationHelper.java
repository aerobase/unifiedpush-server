package org.jboss.aerogear.unifiedpush.rest.authentication;

import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.rest.util.BearerHelper;
import org.jboss.aerogear.unifiedpush.rest.util.ClientAuthHelper;
import org.jboss.aerogear.unifiedpush.rest.util.HttpBasicHelper;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.AliasService;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationService;
import org.jboss.aerogear.unifiedpush.service.GenericVariantService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Controller
public class AuthenticationHelper {
	private static final Logger logger = LoggerFactory.getLogger(AuthenticationHelper.class);

	@Inject
	private ClientInstallationService clientInstallationService;
	@Inject
	private GenericVariantService genericVariantService;
	@Inject
	private PushApplicationService pushApplicationService;
	@Autowired(required = false)
	private AliasService aliasService;

	public PushApplication loadApplicationWhenAuthorized(HttpServletRequest request) {
		return loadApplicationWhenAuthorized(request, null);
	}

	/**
	 * Returns the {@link PushApplication}. First try variant based authentication,
	 * then try application based authentication.
	 *
	 * @param request
	 *            {@link HttpServletRequest}
	 * @param alias
	 *            For application level authentication, make sure alias is
	 *            associated.
	 */
	public PushApplication loadApplicationWhenAuthorized(HttpServletRequest request, String alias) {

		// Extract device token
		String deviceToken = ClientAuthHelper.getDeviceToken(request);

		// Try device based authentication
		if (StringUtils.isNotEmpty(deviceToken)) {
			final Variant variant = loadVariantWhenAuthorized(deviceToken, true, request);

			if (variant == null) {
				return null;
			}

			// Find application by variant
			return pushApplicationService.findByVariantID(variant.getVariantID());
		}

		// Try application based authentication
		PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
				pushApplicationService);

		if (pushApplication == null) {
			logger.warn("UnAuthorized application authentication attempt, credentials ({}) are not authorized",
					HttpBasicHelper.getAuthorizationHeader(request));
			return null;
		}

		// Application authentication can only access associated aliases !
		if (aliasService != null && StringUtils.isNoneEmpty(alias)
				&& aliasService.find(pushApplication.getPushApplicationID(), alias) == null) {
			logger.warn(
					"UnAuthorized application authentication, application ({}) is not authorized for alias ({}) scope data !",
					pushApplication.getName(), alias);
			return null;
		}

		return pushApplication;
	}

	/**
	 * Returns the {@link Installation} if device token exists and request is
	 * authenticated (Basic/Bearer).
	 *
	 * @param request
	 *            {@link HttpServletRequest}
	 *
	 */
	public Optional<Installation> loadInstallationWhenAuthorized(HttpServletRequest request) {
		// Extract device token
		String deviceToken = ClientAuthHelper.getDeviceToken(request);

		Variant variant = loadVariantWhenAuthorized(deviceToken, false, request);

		if (variant != null) {
			return Optional.ofNullable(clientInstallationService
					.findInstallationForVariantByDeviceToken(variant.getVariantID(), deviceToken));
		}

		return Optional.empty();
	}

	/**
	 * Returns the {@link Variant} if device token is present and device is enabled
	 * (forceExistingInstallation). first fetch credentials from basic
	 * authentication, if missing try bearer credentials.
	 *
	 * @param deviceToken
	 *            Base64 decoded device token
	 * @param forceExistingInstallation
	 *            verify installation exists and enabled.
	 * @param request
	 *            {@link HttpServletRequest}
	 */
	private Variant loadVariantWhenAuthorized(String deviceToken, boolean forceExistingInstallation,
			HttpServletRequest request) {

		if (StringUtils.isEmpty(deviceToken)) {
			logger.warn("API request missing device-token header ({}), URI - > {}", deviceToken,
					request.getRequestURI());
			return null;
		}

		// Get variant from basic authentication headers
		Variant variant = ClientAuthHelper.loadVariantWhenAuthorized(genericVariantService, request, false);

		if (variant == null) {
			// Variant is missing, try to extract variant using Bearer
			if (BearerHelper.isBearerExists(request)) {
				variant = loadVariantFromBearerWhenAuthorized(genericVariantService, request);

				if (variant == null) {
					logger.info("UnAuthorized bearer authentication missing variant. device token ({}), URI {}",
							deviceToken, request.getRequestURI());
					return null;
				}
				logger.debug("Authorized bearer authentication to exising variant id: {} API: {}",
						variant.getVariantID(), request.getRequestURI());
			} else {
				// Variant is missing to anonymous/otp mode
				logger.warn("UnAuthorized basic authentication using token-id {} API: {}", deviceToken,
						request.getRequestURI());
				return null;
			}
		}

		if (forceExistingInstallation) {
			// Variant can't be null at this point.
			Installation installation = clientInstallationService
					.findInstallationForVariantByDeviceToken(variant.getVariantID(), deviceToken);

			// Installation should always be present and enabled.
			if (installation == null || installation.isEnabled() == false) {
				logger.info(
						"API request to non-existing / disabled installation variant id: {} API: {} device-token: {}",
						variant.getVariantID(), request.getRequestURI(), deviceToken);
				return null;
			}
		}

		return variant;
	}

	/**
	 * returns variant from the bearer token if it is valid for the request.
	 *
	 * @param genericVariantService
	 *            {@link GenericVariantService}
	 * @param request
	 *            {@link HttpServletRequest}
	 */
	private static Variant loadVariantFromBearerWhenAuthorized(GenericVariantService genericVariantService,
			HttpServletRequest request) {
		// extract the Variant from the Authorization header:
		return BearerHelper.extractVariantFromBearerHeader(extractUsername(), genericVariantService, request);
	}

	/**
	 * Extract the username to be used in multiple queries
	 *
	 * @return current logged in user
	 */
	public static LoggedInUser extractUsername() {
		KeycloakAuthenticationToken token = ((KeycloakAuthenticationToken) SecurityContextHolder.getContext()
				.getAuthentication());

		// Null check for automation scenarios.
		if (token != null && token.getPrincipal() != null) {
			KeycloakPrincipal<?> p = (KeycloakPrincipal<?>) token.getPrincipal();

			KeycloakSecurityContext kcSecurityContext = p.getKeycloakSecurityContext();
			return new LoggedInUser(kcSecurityContext.getToken().getPreferredUsername());
		}

		return new LoggedInUser("NULL");
	}
}
