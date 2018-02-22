package org.jboss.aerogear.unifiedpush.rest.config;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.aerogear.unifiedpush.rest.AbstractEndpoint;
import org.jboss.aerogear.unifiedpush.service.impl.spring.IConfigurationService;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.util.SystemPropertiesJsonParserFactory;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Configuration endpoint to aerobase client applications
 */
@Controller
@Path("/keycloak/config")
public class KeycloakConfigurationEndpoint extends AbstractEndpoint {

	@Inject
	private IConfigurationService configurationService;

	@OPTIONS
	public Response crossOriginForApplication(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	@OPTIONS
	@Path("/gsg")
	public Response crossOriginForGsgApplication(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/*
	 * Default unifiedpush-server (aerobase-app).
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response configurationFile(@Context HttpHeaders headers) {

		ObjectMapper mapper = new ObjectMapper(new SystemPropertiesJsonParserFactory());
		mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
		AdapterConfig adapterConfig = new AdapterConfig();
		adapterConfig.setRealm(configurationService.getUpsRealm());
		adapterConfig.setAuthServerUrl(configurationService.getOAuth2Url());

		// When Running using http, allow none https requests
		adapterConfig.setSslRequired(configurationService.getOAuth2Url().startsWith("https") ? "external" : "none");
		adapterConfig.setPublicClient(true);
		adapterConfig.setResource("aerobase-app");

		try {
			return appendAllowOriginHeader(headers, Response.ok(mapper.writeValueAsString(adapterConfig))).build();
		} catch (JsonProcessingException e) {
			return Response.serverError().build();
		}

	}

	/*
	 * Config to aerobase getting starter guide .
	 */
	@GET
	@Path("/gsg")
	@Produces(MediaType.APPLICATION_JSON)
	public Response configurationGsgFile(@Context HttpHeaders headers) {

		ObjectMapper mapper = new ObjectMapper(new SystemPropertiesJsonParserFactory());
		mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
		AdapterConfig adapterConfig = new AdapterConfig();
		adapterConfig.setRealm(configurationService.getUpsRealm());
		adapterConfig.setAuthServerUrl(configurationService.getOAuth2Url());

		// When Running using http, allow none https requests
		adapterConfig.setSslRequired(configurationService.getOAuth2Url().startsWith("https") ? "external" : "none");
		adapterConfig.setPublicClient(true);
		adapterConfig.setResource("aerobase-gsg");

		try {
			return appendAllowOriginHeader(headers, Response.ok(mapper.writeValueAsString(adapterConfig))).build();
		} catch (JsonProcessingException e) {
			return Response.serverError().build();
		}

	}
}
