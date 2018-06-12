package org.jboss.aerogear.unifiedpush.spring;

import org.jboss.aerogear.unifiedpush.cassandra.CassandraConfig;
import org.jboss.aerogear.unifiedpush.jpa.JPAConfig;
import org.jboss.aerogear.unifiedpush.service.impl.AliasServiceImpl;
import org.jboss.aerogear.unifiedpush.service.impl.spring.IConfigurationService;
import org.jboss.aerogear.unifiedpush.service.metrics.IPushMessageMetricsService;
import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ ConfigurationEnvironment.class, ServiceCacheConfig.class, CassandraConfig.class, JPAConfig.class })
@ComponentScan(basePackageClasses = { AliasServiceImpl.class, IConfigurationService.class,
		IPushMessageMetricsService.class })
public class ServiceConfig {

}
