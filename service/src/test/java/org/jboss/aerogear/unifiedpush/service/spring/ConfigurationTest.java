package org.jboss.aerogear.unifiedpush.service.spring;

import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class })
@ContextConfiguration(classes = { ConfigurationEnvironment.class })
public class ConfigurationTest {

	@Autowired
	private ConfigurationEnvironment configuration;

	@Test
	public void testDefaultConfigurationLoader() {
		Assert.assertTrue(configuration.isPortalMode());
	}

	@Test
	public void testSystemPropertiesOverride() {
		System.setProperty(ConfigurationEnvironment.PROP_PORTAL_MODE, Boolean.FALSE.toString());

		Assert.assertFalse(configuration.isPortalMode());
	}
}
