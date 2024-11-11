package com.paypal.sre.cfbt.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ebay.kernel.cal.util.StackTrace;
import com.ebayinc.platform.services.EndPoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.paypal.sre.cfbt.data.test.AccountInfo;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.testresource.api.rest.AccountRequest;
import com.paypal.sre.cfbt.testresource.api.rest.AccountRequestType;
import com.paypal.sre.cfbt.data.execapi.Test;

/**
 * Client class for cfbttestresourceserv apis
 */
@Component
public class CFBTTestResourceClient {

    @Inject
    @EndPoint(service = "cfbttestresourceserv")
    private WebTarget cfbtTestResourceServ;

    private static final Logger logger = LoggerFactory.getLogger(CFBTTestResourceClient.class);
    private static final String TESTRESOURCESERV_BASE_PATH = "v1/cfbt/";

    /**
     * Wrapper method to load the {@link Parameter} for the specified test
     * 
     * @param testId the id of the test
     * @return the list of {@link Parameter} for the test
     */
    public List<Parameter> loadParameters(String testId, String groupName) {
        List<Parameter> parameterList = new ArrayList<>();

        if (cfbtTestResourceServ == null) {
            return parameterList;
        }
        Response response = cfbtTestResourceServ.path(TESTRESOURCESERV_BASE_PATH + "parameters/")
                .queryParam("test-id", testId).queryParam("group-name", groupName).request().header("Content-Type", "application/json").get();

        String responseString = response.readEntity(String.class);
        if (response.getStatus() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try {
                parameterList = mapper.readValue(responseString, new TypeReference<List<Parameter>>() {
                });

            } catch (Exception e) {
                CFBTLogger.logError(logger, CFBTTestResourceClient.class.getCanonicalName(),
                        "Exception while getting parameters. Root cause : " + StackTrace.getStackTrace(), e);
            }
        } else {
            CFBTLogger.logError(logger, CFBTTestResourceClient.class.getCanonicalName(),
                    "Error while getting parameters. Details : " + responseString);

        }
        return parameterList;
    }

    /**
     * @param test the {@link Test}
     * @return the {@link AccountInfo}
     * @throws Exception
     */
    public AccountInfo getNewAccount(Test test) throws Exception {
        AccountRequest accountRequest = new AccountRequest();
        ObjectMapper objectMapper = new ObjectMapper();
        accountRequest.setAccountRequestType(AccountRequestType.NEW.toString());
        accountRequest.setTestName(test.getName());
        Response response = getNewAccount(accountRequest);
        String responseString = response.readEntity(String.class);
        AccountInfo accountInfo = objectMapper.readValue(responseString, AccountInfo.class);
        return accountInfo;
    }

    /**
     * @param accountRequest the {@link AccountRequest}
     * @return the {@link Response}
     * @throws IOException
     */
    public Response getNewAccount(AccountRequest accountRequest) throws IOException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(accountRequest);
        Response response = cfbtTestResourceServ.path(TESTRESOURCESERV_BASE_PATH + "generated-accounts/").request()
                .header("Content-Type", "application/json")
                .method("POST", Entity.entity(json, MediaType.APPLICATION_JSON));

        return response;
    }

}
