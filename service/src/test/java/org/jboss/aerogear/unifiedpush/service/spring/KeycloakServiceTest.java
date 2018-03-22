package org.jboss.aerogear.unifiedpush.service.spring;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.jboss.aerogear.unifiedpush.service.AbstractBaseServiceTest;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;
import org.jboss.aerogear.unifiedpush.service.impl.spring.IKeycloakService;
import org.jboss.aerogear.unifiedpush.service.impl.spring.KeycloakServiceImpl;
import org.jboss.aerogear.unifiedpush.service.impl.spring.OAuth2Configuration;
import org.jboss.aerogear.unifiedpush.service.impl.spring.OAuth2Configuration.DomainMatcher;
import org.jboss.aerogear.unifiedpush.service.spring.KeycloakServiceTest.KeycloakServiceTestConfig;
import org.jboss.aerogear.unifiedpush.spring.ServiceCacheConfig;
import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { KeycloakServiceTestConfig.class, ServiceCacheConfig.class })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class })
public class KeycloakServiceTest {
	private static final LoggedInUser account = new LoggedInUser(AbstractBaseServiceTest.DEFAULT_USER);

	@Autowired
	private IKeycloakService kcServiceMock;

	@Autowired
	private MockProvider mockProvider;

	@Before
	public void before() {
		MockitoAnnotations.initMocks(this);

		when(mockProvider.get().getVariantIdsFromClient(eq(account), eq("test-client-1")))
				.thenReturn(Arrays.asList("variant-1", "variant-2"));
		when(mockProvider.get().getVariantIdsFromClient(eq(account), eq("test-client-2")))
				.thenReturn(Arrays.asList("variant-3", "variant-4"));

		when(mockProvider.get().setPortalMode(anyBoolean())).thenCallRealMethod();
		when(mockProvider.get().stripAccountName(any())).thenCallRealMethod();
		when(mockProvider.get().stripApplicationName(any())).thenCallRealMethod();

	}

	@Test
	public void cacheTest() {
		List<String> firstInvocation = kcServiceMock.getVariantIdsFromClient(account, "test-client-1");
		assertThat(firstInvocation.get(0), is("variant-1"));

		List<String> secondInvocation = kcServiceMock.getVariantIdsFromClient(account, "test-client-1");
		assertThat(secondInvocation.get(0), is("variant-1"));

		verify(mockProvider.get(), times(1)).getVariantIdsFromClient(account, "test-client-1");
	}

	@Test
	public void testDomains() {
		assertThat(DomainMatcher.DOT.matches("aerobase.io", "test.aerobase.io"), is("test"));
		assertThat(DomainMatcher.DOT.matches("aerobase.io", "a-bc.test.aerobase.io"), is("a-bc"));
		assertThat(DomainMatcher.DASH.matches("aerobase.io", "test-aerobase.io"), is("test"));
		assertThat(DomainMatcher.DASH.matches("aerobase.io", "a-bc-test-aerobase.io"), is("a-bc-test"));
		assertThat(DomainMatcher.DASH.matches("aerobase.io", "a-bc-test-aerobase.io"), is("a-bc-test"));
	}

	@Test
	public void testSccounts() {
		assertThat(KeycloakServiceImpl.toRealm("admin", new LoggedInUser("admin"), null), is("master"));
		assertThat(KeycloakServiceImpl.toRealm("admin", new LoggedInUser("test@aerobase.org"), null),
				is("test-aerobase-org"));
		assertThat(KeycloakServiceImpl.toRealm("admin", new LoggedInUser("test_123_123@aerobase.org"), null),
				is("test-123-123-aerobase-org"));
		assertThat(KeycloakServiceImpl.toRealm("admin", new LoggedInUser("test__--123@aerobase.org"), null),
				is("test----123-aerobase-org"));
	}

	@Test
	public void testStripAccounts() {
		kcServiceMock.setPortalMode(true);
		assertThat(kcServiceMock.stripAccountName("test1.aerobase.io"), is("test1"));
		assertThat(kcServiceMock.stripAccountName("app_name.test1.aerobase.io"), is("test1"));

		kcServiceMock.setPortalMode(false);
		assertThat(kcServiceMock.stripAccountName("test1.aerobase.io"), is("master"));
		assertThat(kcServiceMock.stripAccountName("teatapp.test1.aerobase.io"), is("master"));

		kcServiceMock.setPortalMode(true);
		// Performance check
		for (int i = 1; i < 10000; i++) {
			assertThat(kcServiceMock.stripAccountName("app_name.test" + i + ".aerobase.io"), is("test" + i));
		}
	}

	@Test
	public void testStripApplication() {
		kcServiceMock.setPortalMode(true);
		assertThat(kcServiceMock.stripApplicationName("test1.aerobase.io"), is("test1"));
		assertThat(kcServiceMock.stripApplicationName("app_name.test1.aerobase.io"), is("app_name"));

		kcServiceMock.setPortalMode(false);
		assertThat(kcServiceMock.stripApplicationName("app_name.aerobase.io"), is("app_name"));
		assertThat(kcServiceMock.stripApplicationName("app_name.test1.aerobase.io"), is("app_name"));

		// Performance check
		for (int i = 1; i < 10000; i++) {
			assertThat(kcServiceMock.stripApplicationName("app_name.test" + i + ".aerobase.io"), is("app_name"));
		}
	}

	@After
	public void validate() {
		validateMockitoUsage();
	}

	@Configuration
	@Import({ ConfigurationEnvironment.class, OAuth2Configuration.class })
	static class KeycloakServiceTestConfig {
		private KeycloakServiceImpl mockKcService = mock(KeycloakServiceImpl.class);

		@Bean
		public KeycloakServiceImpl kcServiceMock() {
			return mockKcService;
		}

		@Bean
		public MockProvider mockProvider() {
			return new MockProvider(mockKcService);
		}
	}

	public static class MockProvider {
		private final IKeycloakService repository;

		public MockProvider(IKeycloakService repository) {
			this.repository = repository;
		}

		public IKeycloakService get() {
			return this.repository;
		}

	}

}
