package org.jboss.aerogear.unifiedpush.service.impl.spring;

import java.util.Properties;

import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * EJB singleton wrapper bean. Wraps spring configuration environments and beans.
 */
@Service
public class ConfigurationServiceImpl implements IConfigurationService {
	@Autowired
	private ConfigurationEnvironment environment;
	@Autowired
	private IOAuth2Configuration oAuth2Configuration;

	/*
	 * Number of days period to query existing documents.
	 */
	public Integer getQueryDefaultPeriodInDays() {
		return environment.getQueryDefaultPeriodInDays();
	}

	public Properties getProperties() {
		return environment.getProperties();
	}

	public String getOAuth2Url() {
		return oAuth2Configuration.getOAuth2Url();
	}

	public String getUpsRealm() {
		return oAuth2Configuration.getUpsRealm();
	}
}
