package org.jboss.aerogear.unifiedpush.service.spring;

import org.jboss.aerogear.unifiedpush.service.impl.spring.KeycloakServiceImpl.AccountNameMatcher;
import org.junit.Assert;
import org.junit.Test;

public class AccountNameMatcherTest {

	@Test
	public void testSpecialCherecters() {
		Assert.assertTrue(AccountNameMatcher.matches("yaniv_test@test.com").equals("yaniv-test-test-com"));
	}
}
