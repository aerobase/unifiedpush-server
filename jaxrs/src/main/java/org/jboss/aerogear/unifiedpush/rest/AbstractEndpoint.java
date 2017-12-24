package org.jboss.aerogear.unifiedpush.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;

public class AbstractEndpoint {
	private static final String REFERER_HEADER = "Referer";

	protected static ResponseBuilder appendPreflightResponseHeaders(HttpHeaders headers, ResponseBuilder response) {
        // add response headers for the preflight request
        // required
        response.header("Access-Control-Allow-Origin", headers.getRequestHeader("Origin").get(0)) // return submitted origin
                .header("Access-Control-Allow-Methods", "POST, DELETE, PUT, GET, OPTIONS")
                .header("Access-Control-Allow-Headers", "accept, origin, content-type, authorization, device-token") // explicit Headers!
                .header("Access-Control-Allow-Credentials", "true")
                // indicates how long the results of a preflight request can be cached (in seconds)
                .header("Access-Control-Max-Age", "604800"); // for now, we keep it for seven days

        return response;
    }

	protected Response appendAllowOriginHeader(ResponseBuilder rb, HttpServletRequest request) {
		// "enable-cors" : true, is available at upsi.json (Subdomain proxy),
		// therefore we want to prevent duplicated cors when using rest context.
		//
		if (!isProxyRequest(request))
			return rb.header("Access-Control-Allow-Origin", request.getHeader("Origin")) // return submitted origin
					.header("Access-Control-Allow-Credentials", "true")
					.build();
		else
			return rb.build();
    }

	protected Response create401Response(final HttpServletRequest request) {
        return appendAllowOriginHeader(
                Response.status(Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
                        .entity(quote("Unauthorized Request")),
                request);
    }

    // Append double quotes to strings, used to overcome jax-rs issue with simple stings.
    // http://stackoverflow.com/questions/7705081/jax-rs-resteasy-service-return-json-string-without-double-quote
    protected static String quote(String value) {
    	return new StringBuilder(value.length() + 2).append('"' + value + '"').toString();
    }

	private Boolean isProxyRequest(HttpServletRequest request) {
		String referer = request.getHeader(REFERER_HEADER);
		return StringUtils.isNoneEmpty(referer) && !request.getRequestURI().startsWith(referer);
	}
}
