package com.paypal.test.sre.utilities;

import com.paypal.selion.platform.asserts.SeLionAsserts;
import org.apache.commons.httpclient.HttpStatus;

/**
 * Validate HTTP status after making a service call to any RESTful Java service.
 */
public class ValidateHTTPStatus {

    /**
     * Validate HTTP Success status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpSuccess(String reasonPhrase, int statusCode) {
        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_OK), " *** Verify HTTP success reason OK *** ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_OK, " *** Verify HTTP status code 200 *** ");

    }

    /**
     * Validate HTTP BadRequest status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpBadRequest(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_BAD_REQUEST),
                " *** Verify HTTP success reason Bad Request *** ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_BAD_REQUEST, " *** Verify HTTP status code 400 *** ");

    }

    /**
     * Validate HTTP NotFound status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpNotFound(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_NOT_FOUND),
                " Verify HTTP success reason phrase 'Not Found' ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_NOT_FOUND, " ** Verify HTTP success status code '404' ** ");

    }

    /**
     * Validate HTTP NoContent status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpNoContent(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_NO_CONTENT),
                " Verify HTTP success reason phrase 'No Content' ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_NO_CONTENT, " ** Verify HTTP success status code '204' ** ");

    }

    /**
     * Validate HTTP Conflict status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpConflict(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_CONFLICT),
                " Verify HTTP success reason phrase 'Conflict' ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_CONFLICT, " ** Verify HTTP success status code '409' ** ");

    }

    /**
     * Validate HTTP Unauthorized status after making a service call to cfbtmanagementserv.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpUnauthorized(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_UNAUTHORIZED),
                " Verify HTTP success reason phrase httpUnauthorized ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_UNAUTHORIZED, " ** Verify HTTP success status code '401' ** ");
    }

    /**
     * 
     * Validate HTTP Forbidden Error status after making a service call to the respective service.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void forbidden(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(HttpStatus.getStatusText(HttpStatus.SC_FORBIDDEN), reasonPhrase,
                " Verify HTTP success reason phrase forbidden ");
        SeLionAsserts.verifyEquals(HttpStatus.SC_FORBIDDEN, statusCode, " ** Verify HTTP success status code '403' ** ");

    }

    /**
     * Http internal server error.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpInternalServerError(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_INTERNAL_SERVER_ERROR),
                " *** Verify HTTP status response is Internal Server Error*** ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_INTERNAL_SERVER_ERROR, " *** Verify HTTP status code 500 *** ");

    }

    /**
     * Http unprocessable entity error.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpUnprocessableEntityError(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_UNPROCESSABLE_ENTITY),
                " *** Verify HTTP status response is Unprocessable Entity*** ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_UNPROCESSABLE_ENTITY, " *** Verify HTTP status code 422 *** ");

    }

    /**
     * Http Created.
     * 
     * Validate HTTP Success status after making a service call to cfbtmanagementserv. The request has been fulfilled and
     * resulted in a new resource being created. The newly created resource can be referenced by the URI(s) returned in
     * the entity of the response, with the most specific URI for the resource given by a Location header field. The
     * response SHOULD include an entity containing a list of resource characteristics and location(s) from which the
     * user or user agent can choose the one most appropriate.
     * 
     * @param reasonPhrase
     *            the reason phrase
     * @param statusCode
     *            the status code
     */
    public static void httpCreated(String reasonPhrase, int statusCode) {

        SeLionAsserts.verifyEquals(reasonPhrase, HttpStatus.getStatusText(HttpStatus.SC_CREATED),
                " *** Verify HTTP response is Created*** ");
        SeLionAsserts.verifyEquals(statusCode, HttpStatus.SC_CREATED, " *** Verify HTTP status code 201 *** ");

    }

}