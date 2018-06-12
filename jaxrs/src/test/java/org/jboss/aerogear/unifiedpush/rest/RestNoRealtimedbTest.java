package org.jboss.aerogear.unifiedpush.rest;

import org.jboss.aerogear.unifiedpush.service.AbstractNoCassandraServiceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;

public abstract class RestNoRealtimedbTest extends AbstractNoCassandraServiceTest implements IRestEndpointTest {
	@Autowired
	protected TestRestTemplate testTemplate;
	@LocalServerPort
	private int port;

	protected String getRestFullPath() {
		return "http://localhost:" + port + "/" + RESOURCE_PREFIX;
	}
	
	protected void specificSetup(){

	}
}
