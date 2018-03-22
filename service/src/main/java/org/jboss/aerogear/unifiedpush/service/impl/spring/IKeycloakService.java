package org.jboss.aerogear.unifiedpush.service.impl.spring;

import java.util.List;

import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;
import org.springframework.cache.annotation.Cacheable;

public interface IKeycloakService {
	public static final String CACHE_NAME = "variant-ids-per-clientid";

	void createRealmIfAbsent(LoggedInUser accountName, PushApplication pushApplication);

	void createClientIfAbsent(LoggedInUser accountName, PushApplication pushApplication);

	void removeClient(LoggedInUser accountName, PushApplication pushApplicaiton);

	void createVerifiedUserIfAbsent(LoggedInUser accountName, String userName, String password);

	boolean exists(LoggedInUser accountName, String userName);

	void delete(LoggedInUser accountName, String userName);

	@Cacheable(value = IKeycloakService.CACHE_NAME, unless = "#result == null")
	List<String> getVariantIdsFromClient(LoggedInUser accountName, String clientId);

	void updateUserPassword(LoggedInUser accountName, String aliasId, String currentPassword, String newPassword);

	boolean isInitialized();

	String stripApplicationName(String fqdn);

	String stripAccountName(String fqdn);

	String toRealmName(LoggedInUser account);

	String getClientName(LoggedInUser account, String applicationName);

	Boolean setPortalMode(Boolean mode);
}
