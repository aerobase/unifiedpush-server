package org.jboss.aerogear.unifiedpush.service.impl.spring;

import java.util.Properties;

public interface IConfigurationService {
	Properties getProperties();

	Integer getQueryDefaultPeriodInDays();

	String getOAuth2Url();

	String getUpsRealm();
}
