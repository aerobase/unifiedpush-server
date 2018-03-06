package org.jboss.aerogear.unifiedpush.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.jboss.aerogear.unifiedpush.rest.authentication.AuthenticationHelper;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedInUser;

public class AbstractEndpoint {
	protected static ResponseBuilder appendPreflightResponseHeaders(HttpHeaders headers, ResponseBuilder response) {
        // add response headers for the preflight request
        // required
        response.header("Access-Control-Allow-Origin", headers.getRequestHeader("Origin").get(0)) // return submitted origin
        .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Methods", "POST, DELETE, PUT, GET, OPTIONS")
                .header("Access-Control-Allow-Headers", "accept, origin, content-type, authorization, device-token") // explicit Headers!
                // indicates how long the results of a preflight request can be cached (in seconds)
                .header("Access-Control-Max-Age", "604800"); // for now, we keep it for seven days

        return response;
    }

	protected ResponseBuilder appendAllowOriginHeader(HttpHeaders headers, ResponseBuilder response) {
		if (headers.getRequestHeader("Origin").size() > 0){
			return response.header("Access-Control-Allow-Origin", headers.getRequestHeader("Origin").get(0)) // return submitted origin
				.header("Access-Control-Allow-Credentials", "true");
		}

		return response;
    }

	protected Response appendAllowOriginHeader(ResponseBuilder rb, HttpServletRequest request) {
		return rb.header("Access-Control-Allow-Origin", request.getHeader("Origin")) // return submitted origin
				.header("Access-Control-Allow-Credentials", "true")
				.build();
    }

	protected Response create401Response(final HttpServletRequest request) {
        return appendAllowOriginHeader(
                Response.status(Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
                        .entity(quote("Unauthorized Request")),
                request);
    }


    /**
     * Helper function to create a 400 Bad Request response, containing a JSON giving details about the response.
     *
     * @param message response error message
     * @return 400 Bad Request response, containing details on the violations
     */
	protected Response create400Response(String message, final HttpServletRequest request) {
        return appendAllowOriginHeader(
                Response.status(Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Basic realm=\"AeroBase UnifiedPush Server\"")
                        .entity(quote(message)),
                request);
	}


    // Append double quotes to strings, used to overcome jax-rs issue with simple stings.
    // http://stackoverflow.com/questions/7705081/jax-rs-resteasy-service-return-json-string-without-double-quote
    protected static String quote(String value) {
    	return new StringBuilder(value.length() + 2).append('"' + value + '"').toString();
    }

	/**
	 * Extract the username to be used in multiple queries
	 *
	 * @return current logged in user
	 */
    public static LoggedInUser extractUsername() {
       return AuthenticationHelper.extractUsername();
    }
}
