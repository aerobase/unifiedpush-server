package org.jboss.aerogear.unifiedpush.service.impl.spring;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;
import org.jboss.aerogear.unifiedpush.service.impl.spring.OAuth2Configuration.DomainMatcher;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.JsonSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.common.net.InternetDomainName;

@Service
public class KeycloakServiceImpl implements IKeycloakService {
	private static final Logger logger = LoggerFactory.getLogger(KeycloakServiceImpl.class);

	private static final String CLIENT_SUFFIX = "client";
	private static final String CLIENT_SEPARATOR = "-";
	private static final String KEYCLOAK_ROLE_USER = "installation";
	private static final String UPDATE_PASSWORD_ACTION = "UPDATE_PASSWORD";

	private static final String ATTRIBUTE_VARIANT_SUFFIX = "_variantid";
	private static final String ATTRIBUTE_SECRET_SUFFIX = "_secret";

	private volatile Boolean oauth2Enabled;
	private volatile Boolean portalMode;
	private Keycloak kc;

	// TODO - Convert to infinispan cache
	private static final Map<String, RealmResource> realmCache = new ConcurrentHashMap<String, RealmResource>();

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private IOAuth2Configuration conf;

	// We don't Initialized with @PostConstruct to avoid startup failures.
	public boolean isInitialized() {
		if (!conf.isOAuth2Enabled()) {
			return false;
		}

		if (oauth2Enabled == null) {
			synchronized (this) {
				if (oauth2Enabled == null) {
					oauth2Enabled = conf.isOAuth2Enabled();
					portalMode = conf.isPortalMode();

					if (oauth2Enabled) {
						this.initialize(portalMode);
					}
				}
			}
		}

		return oauth2Enabled.booleanValue();
	}

	private void initialize(boolean portalMode) {
		String keycloakPath = conf.getOAuth2Url();

		String cliClientId = conf.getAdminClient();
		String userName = conf.getAdminUserName();
		String userPassword = conf.getAdminPassword();

		// Always use master realm for portal mode.
		this.kc = KeycloakBuilder.builder() //
				.serverUrl(keycloakPath) //
				.realm(portalMode ? OAuth2Configuration.DEFAULT_OAUTH2_UPS_REALM : conf.getUpsRealm()) //
				.username(userName) //
				.password(userPassword) //
				.clientId(cliClientId) //
				.resteasyClient( //
						// Setting TTL to 10 seconds, prevent KC token
						// expiration.
						new ResteasyClientBuilder().connectionPoolSize(25).connectionTTL(10, TimeUnit.SECONDS).build()) //
				.build();
	}

	/*
	 * Return realm resource according to account name. Account name special
	 * characters are replaces. </b> e.g: account support@aerobase.com become
	 * support-aerobase-com realm.
	 */
	private RealmResource getRealm(LoggedInUser accountName) {
		return getRealm(accountName, false);
	}

	private RealmResource getRealm(LoggedInUser accountName, boolean flashCache) {
		String realmName = toRealmName(accountName);

		if (flashCache && realmCache.containsKey(realmName)) {
			realmCache.remove(realmName);
		}

		if (realmCache.containsKey(realmName)) {
			return realmCache.get(realmName);
		} else {
			RealmResource realm = kc.realms().realm(realmName);

			// Realm exists in remote KC
			try {
				setRealmConfiguration(realm);
				realmCache.put(realmName, realm);
			} catch (Exception e) {
				logger.debug("Messing realm " + realmName);
				return null;
			}
			return realm;
		}
	}

	public String toRealmName(LoggedInUser account) {
		return toRealm(conf.getAdminUserName(), account, conf);
	}

	public static String toRealm(String adminAccountName, LoggedInUser account, IOAuth2Configuration conf) {
		if (account.getUser().equals(adminAccountName)) {
			if (conf == null || conf.isPortalMode()) // Always return master
				return OAuth2Configuration.DEFAULT_OAUTH2_UPS_REALM;
			else {
				return conf.getUpsRealm();
			}
		}

		return AccountNameMatcher.matches(account.getUser());
	}

	private void setRealmConfiguration(RealmResource realm) {
		RealmRepresentation realmRepresentation = realm.toRepresentation();
		realmRepresentation.setRememberMe(true);
		realmRepresentation.setResetPasswordAllowed(true);
	}

	@Override
	public void createRealmIfAbsent(LoggedInUser accountName, PushApplication pushApplication) {
		if (!isInitialized()) {
			return;
		}
		// Evict realm from cache and fetch from KC
		RealmResource realm = getRealm(accountName, true);

		// Realm is missing in KC
		if (realm == null) {

			// Import realm first
			RealmRepresentation rep = loadJson(getClass().getResourceAsStream("/realm.json"),
					RealmRepresentation.class);

			// Replace strings if exists
			rep.setRealm(toRealmName(accountName));

			// Create realm
			kc.realms().create(rep);

			// Get client from master realm, XXX-realm client is auto generate
			// for each realm.
			ClientRepresentation client = isClientExists(new LoggedInUser(conf.getAdminUserName()),
					rep.getRealm() + "-realm");

			// Update Aerobase account with relevant permissions
			if (client != null) {
				// Get User by username
				UserRepresentation user = getUser(new LoggedInUser(conf.getAdminUserName()), accountName.get());

				UsersResource users = getRealm(new LoggedInUser(conf.getAdminUserName())).users();
				UserResource userResource = users.get(user.getId());

				List<RoleRepresentation> availableRoles = userResource.roles().clientLevel(client.getId())
						.listAvailable();
				userResource.roles().clientLevel(client.getId()).add(availableRoles);
			} else {
				logger.error(
						"Unable to find client Representation for newly created realm - " + toRealmName(accountName));
			}

			// Create default client
			createClientIfAbsent(accountName, null);
		}
	}

	public static <T> T loadJson(InputStream is, Class<T> type) {
		try {
			return JsonSerialization.readValue(is, type);
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse json", e);
		}
	}

	@Override
	public void createClientIfAbsent(LoggedInUser account, PushApplication pushApplication) {
		if (!isInitialized()) {
			return;
		}

		String applicationName = getAppName(pushApplication);
		String clientName = getClientName(account, getAppName(pushApplication));
		ClientRepresentation clientRepresentation = isClientExists(account, pushApplication);

		if (this.oauth2Enabled && clientRepresentation == null) {
			clientRepresentation = new ClientRepresentation();

			clientRepresentation.setClientId(clientName);
			clientRepresentation.setEnabled(true);

			String domain = conf.getRooturlDomain();
			String protocol = conf.getRooturlProtocol();
			clientRepresentation.setRootUrl(
					conf.getRooturlMatcher().rootUrl(protocol, domain, toRealmName(account), applicationName));
			clientRepresentation.setRedirectUris(Arrays.asList("/*"));
			clientRepresentation.setBaseUrl("/");

			clientRepresentation.setStandardFlowEnabled(true);
			clientRepresentation.setPublicClient(true);
			clientRepresentation.setWebOrigins(Arrays.asList("*"));

			clientRepresentation.setAttributes(getClientAttributes(pushApplication));
			getRealm(account).clients().create(clientRepresentation);
		} else {
			ClientResource clientResource = getRealm(account).clients().get(clientRepresentation.getId());
			clientRepresentation.setAttributes(getClientAttributes(pushApplication));
			clientResource.update(clientRepresentation);
			// Evict from cache
			evict(clientRepresentation.getId());
		}
	}

	public void removeClient(LoggedInUser account, PushApplication pushApplicaiton) {
		if (!isInitialized()) {
			return;
		}

		ClientRepresentation client = isClientExists(account, pushApplicaiton);

		if (client != null) {
			getRealm(account).clients().get(client.getId()).remove();
		}
	}

	/**
	 * Create verified user by username (If Absent).
	 *
	 * Create user must be done synchronously and prevent clients from
	 * authenticating before KC operation is complete.
	 *
	 * @param userName
	 *            unique userName
	 * @param password
	 *            password
	 */
	public void createVerifiedUserIfAbsent(LoggedInUser accountName, String userName, String password) {
		if (!isInitialized()) {
			return;
		}

		UserRepresentation user = getUser(accountName, userName);

		if (user == null) {
			user = create(userName, password, true);

			getRealm(accountName).users().create(user);

			// TODO - Improve implementation, check why we need to update the
			// user right upon creation. without calling updateUserPassword
			// password is invalid.
			if (StringUtils.isNotEmpty(password)) {
				updateUserPassword(accountName, userName, password, password);
			}
		} else {
			logger.debug("KC Username {}, already exist", userName);
		}
	}

	private UserRepresentation create(String userName, String password, boolean enabled) {
		UserRepresentation user = new UserRepresentation();
		user.setUsername(userName);

		user.setRequiredActions(Arrays.asList(UPDATE_PASSWORD_ACTION));
		user.setRealmRoles(Collections.singletonList(KEYCLOAK_ROLE_USER));

		user.setEnabled(enabled);

		if (StringUtils.isNotEmpty(password)) {
			user.setEmailVerified(true);
			user.setEmail(userName);

			user.setCredentials(Arrays.asList(getUserCredentials(password)));
		}

		return user;
	}

	public boolean exists(LoggedInUser accountName, String userName) {
		if (!isInitialized()) {
			return false;
		}

		UserRepresentation user = getUser(accountName, userName);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", userName));
			return false;
		}

		return true;
	}

	@Async
	public void delete(LoggedInUser accountName, String userName) {
		if (!isInitialized()) {
			return;
		}

		if (StringUtils.isEmpty(userName)) {
			logger.warn("Cancel attempt to remove empty or null username");
			return;
		}

		UserRepresentation user = getUser(accountName, userName);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", userName));
			return;
		}

		getRealm(accountName).users().delete(user.getId());
	}

	@Override
	public List<String> getVariantIdsFromClient(LoggedInUser accountName, String clientId) {
		if (!isInitialized()) {
			return null;
		}

		ClientRepresentation client = isClientExists(accountName, clientId);

		List<String> variantIds = null;
		if (client != null) {
			Map<String, String> attributes = client.getAttributes();
			if (attributes != null) {
				variantIds = new ArrayList<String>(attributes.size());
				for (Map.Entry<String, String> entry : attributes.entrySet()) {
					if (entry.getKey().endsWith(ATTRIBUTE_VARIANT_SUFFIX)) {
						variantIds.add(entry.getValue());
					}
				}
			}
		}

		return variantIds;
	}

	@Override
	public void updateUserPassword(LoggedInUser accountName, String aliasId, String currentPassword,
			String newPassword) {
		UserRepresentation user = getUser(accountName, aliasId);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", aliasId));
			return;
		}

		boolean isCurrentPasswordValid = isCurrentPasswordValid(user, currentPassword);

		if (isCurrentPasswordValid == true) {
			UsersResource users = getRealm(accountName).users();
			UserResource userResource = users.get(user.getId());

			userResource.resetPassword(getUserCredentials(newPassword));
		}
	}

	private boolean isCurrentPasswordValid(UserRepresentation user, String currentPassword) {
		// TODO: add current password validations
		return true;
	}

	private CredentialRepresentation getUserCredentials(String password) {
		CredentialRepresentation credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(password);

		return credential;
	}

	private UserRepresentation getUser(LoggedInUser accountName, String username) {
		List<UserRepresentation> users = getRealm(accountName).users().search(username, 0, 1);
		if (users != null && users.size() > 0) {
			return users.get(0);
		}

		return null;
	}

	private ClientRepresentation isClientExists(LoggedInUser account, PushApplication pushApplication) {
		return isClientExists(account, getClientName(account, getAppName(pushApplication)));
	}

	private String getAppName(PushApplication pushApplicatoin) {
		if (pushApplicatoin == null) {
			return null;
		}
		// Use account matcher to remove special characters.
		return AccountNameMatcher.matches(pushApplicatoin.getName()).toLowerCase();
	}

	public String getClientName(LoggedInUser account, String applicationName) {
		String accountName = AccountNameMatcher.matches(account.getUser());
		if (applicationName == null) {
			return accountName + CLIENT_SEPARATOR + CLIENT_SUFFIX;
		} else {
			return applicationName + CLIENT_SEPARATOR + accountName + CLIENT_SEPARATOR + CLIENT_SUFFIX;
		}
	}

	private ClientRepresentation isClientExists(LoggedInUser accountName, String clientId) {
		RealmResource realmResource = getRealm(accountName);

		// Realm was either removed or no permission to view this realm/
		if (realmResource == null) {
			return null;
		}

		List<ClientRepresentation> clients = realmResource.clients().findByClientId(clientId);

		if (clients == null | clients.size() == 0) {
			return null;
		}

		// Return first client
		return clients.get(0);
	}

	private Map<String, String> getClientAttributes(PushApplication pushApp) {
		if (pushApp==null){
			return Collections.emptyMap();
		}

		List<Variant> variants = pushApp.getVariants();
		Map<String, String> attributes = new HashMap<>(variants.size());
		for (Variant variant : variants) {
			String varName = variant.getName().toLowerCase();
			attributes.put(varName + ATTRIBUTE_VARIANT_SUFFIX, variant.getVariantID());
			attributes.put(varName + ATTRIBUTE_SECRET_SUFFIX, variant.getSecret());
		}

		return attributes;
	}

	private void evict(String clientId) {
		Cache cache = cacheManager.getCache(IKeycloakService.CACHE_NAME);
		cache.evict(clientId);
	}

	public String stripApplicationName(String fqdn) {
		// TODO - If fqdn does not have top private domain as
		// conf.getRooturlDomain()
		// Extract account according to application alternate name (Hosting
		// Support).

		// for both portal/regular mode application name is the first part.
		// Support regex strip for backward compatibility with _ separator.
		return strip(fqdn);
	}

	public String stripAccountName(String fqdn) {
		// none portal mode, always use environment attribute as account name.
		if (!portalMode) {
			return conf.getUpsRealm();
		} else {
			// TODO - If fqdn does not have top private domain as
			// conf.getRooturlDomain()
			// Extract account according to application alternate name (Hosting
			// Support).

			InternetDomainName domain = InternetDomainName.from(fqdn);
			// if only one sub domain exists strip it
			if (domain.parent().isTopPrivateDomain())
				return domain.parts().get(0);
			else {
				// strip parent (second sub domain)
				return domain.parent().parts().get(0);
			}
		}
	}

	/*
	 * Strip and return first subdomain according to matcher and separator.
	 * separator character can be either '-' or '.' or '*'; TODO - Make sure
	 * application name is unique.
	 */
	private String strip(String fqdn) {
		String domain = conf.getRooturlDomain();
		DomainMatcher matcher = conf.getRooturlMatcher();

		if (StringUtils.isNotEmpty(fqdn)) {
			return matcher.matches(domain, fqdn);
		}

		return StringUtils.EMPTY;
	}

	static final class AccountNameMatcher {
		private static final Pattern pattern = Pattern.compile("[^a-zA-Z0-9]");

		public static String matches(String toMatch) {
			Matcher matcher = pattern.matcher(toMatch);

			return matcher.replaceAll("-").toLowerCase();
		}
	}

	// protected for testing mode
	public Boolean setPortalMode(Boolean portalMode) {
		this.portalMode = portalMode;
		return portalMode;
	}


}
