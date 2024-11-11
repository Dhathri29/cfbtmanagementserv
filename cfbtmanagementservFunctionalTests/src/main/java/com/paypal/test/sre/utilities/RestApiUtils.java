package com.paypal.test.sre.utilities;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import org.json.JSONException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paypal.test.jaws.config.JawsConfig;
import com.paypal.test.jaws.config.JawsConfig.JawsConfigProperty;
import com.paypal.test.jaws.http.rest.PayPalRestClient;
import com.paypal.test.jaws.http.rest.PayPalRestClientFactory;
import com.paypal.test.jaws.logging.JawsLogger;

/**
 * This class is responsible to handle Rest Api operations.
 */
public class RestApiUtils {

    private static final String CFBTMANAGEMENTSERVPORT = "18763";

    /**
     * Common method to execute get call and will return the response
     */
    public static JsonNode doGet(PayPalRestClient<JsonNode> client) {
        client.addHeader("Content-Type", "application/json");
        client.addHeader("X-CFBT-USER-INFO", "{\"userid\": \"testUser\",\"scopes\": [\"PP_CFBT_Administrator\"]}");
        JsonNode getResponse = client.get();
        JawsLogger.getLogger().info("Response: " + getResponse.toString());
        JawsLogger.getLogger().info("Status Code: " + client.getStatus().getStatusCode());
        JawsLogger.getLogger().info("Reason Phrase: " + client.getStatus().getReasonPhrase());
        return getResponse;
    }

    /**
     * Common method to execute patch call and will return the response
     */
    public static JsonNode doPatch(PayPalRestClient<JsonNode> client, ArrayNode input) {
        client.addHeader("Content-Type", "application/json");
        client.addHeader("X-CFBT-USER-INFO", "{\"userid\": \"testUser\",\"scopes\": [\"PP_CFBT_Administrator\"]}");
        JsonNode getResponse = client.patch(input);
        JawsLogger.getLogger().info("Request: " + input.toString());
        JawsLogger.getLogger().info("Response: " + getResponse.toString());
        JawsLogger.getLogger().info("Status Code: " + client.getStatus().getStatusCode());
        JawsLogger.getLogger().info("Reason Phrase: " + client.getStatus().getReasonPhrase());
        return getResponse;
    }

    /**
     * Common method to execute post call and will return the response
     */
    public static JsonNode doPost(PayPalRestClient<JsonNode> client, JsonNode input) {
        JsonNode getResponse = client.post(input);
        JawsLogger.getLogger().info("Request: " + input.toString());
        JawsLogger.getLogger().info("Response: " + getResponse.toString());
        JawsLogger.getLogger().info("Status Code: " + client.getStatus().getStatusCode());
        JawsLogger.getLogger().info("Reason" + "Phrase: " + client.getStatus().getReasonPhrase());
        return getResponse;
    }

    /**
     * Gets the patch request for the changed fields.
     * 
     * @return the request patch with origination application id
     * @throws JSONException
     *             the JSON exception
     */
    public static ArrayNode getRequestPatchId(String opName, HashMap<String, ?> pathValue) throws JSONException {
        ArrayNode jsonItemArray = new ObjectMapper().createArrayNode();
        for (Iterator<String> itr = pathValue.keySet().iterator(); itr.hasNext();) {
            String key = itr.next();
            ObjectNode jsonItem1 = new ObjectMapper().createObjectNode();
            jsonItem1.put("op", opName);
            jsonItem1.put("path", key);
            if (pathValue.get(key) instanceof Integer) {
                jsonItem1.put("value", (Integer) pathValue.get(key));
            } else {
                jsonItem1.put("value", pathValue.get(key).toString());
            }
            jsonItemArray.add(jsonItem1);
        }
        JawsLogger.getLogger().info("SC Object : " + jsonItemArray.toString());
        return jsonItemArray;
    }

    /**
     * Gets the request with input parameter.
     *
     * @return the request patch with rest URI
     * @throws JSONException
     *             the JSON exception
     */
    public static PayPalRestClient<JsonNode> restParameterClientBase(String restParameter) throws JSONException, Exception {
        URL requestURL = new URL(getBaseUrl() + restParameter);
        PayPalRestClient<JsonNode> client = PayPalRestClientFactory.createJSONClient(requestURL);
        JawsLogger.getLogger().info("Request URL: " + requestURL.toString());
        return (PayPalRestClient<JsonNode>) client;
    }

    /**
     * Gets the base request for plain text response.
     *
     * @return the request patch with rest URI
     * @throws JSONException
     *             the JSON exception
     */
    public static PayPalRestClient<String> restClientBasePlainText(String restParameter) throws JSONException, Exception {
        URL requestURL = new URL(getBaseUrl() + restParameter);
        PayPalRestClient<String> client = PayPalRestClientFactory.createGenericClient(requestURL);
        JawsLogger.getLogger().info("Request URL: " + requestURL.toString());
        return (PayPalRestClient<String>) client;
    }

    /**
     * Common method to execute get call and will return the plain text response
     */
    public static String doGetAndReturnPlainTextResponse(PayPalRestClient<String> client) {
        String response = client.get();
        JawsLogger.getLogger().info("Response: " + response);
        JawsLogger.getLogger().info("Status Code: " + client.getStatus().getStatusCode());
        JawsLogger.getLogger().info("Reason Phrase: " + client.getStatus().getReasonPhrase());
        return response;
    }

    /**
     * Method used to setup the base URL for REST Call
     * 
     * @return string baseUrl
     */
    public static String getBaseUrl() {
        JawsLogger.getLogger()
                .info("Url: " + "https://" + JawsConfig.getConfigProperty(JawsConfigProperty.HOSTNAME).toLowerCase()
                        + ":" + CFBTMANAGEMENTSERVPORT + "/v2/cfbt/");
        return "https://" + JawsConfig.getConfigProperty(JawsConfigProperty.HOSTNAME).toLowerCase() + ":"
                + CFBTMANAGEMENTSERVPORT + "/v2/cfbt/";
    }

}
