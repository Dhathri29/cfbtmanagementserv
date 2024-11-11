package com.paypal.sre.cfbt.management.rest.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.execapi.UserInfo;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to encapsulate CFBT Role constants and access control logic.
 * @author todd
 */
public class RBAC {
    
    private static final Logger logger = LoggerFactory.getLogger(RBAC.class);

    /*
     * Authoritative list of CFBT roles.
     * TODO Populate these strings via DI.
     *
     * CFBT Role "Technical Results" string constant.
     */
    public static final String TECHNICAL_RESULTS = "PP_CFBT_TechnicalResults";

    /**
     * CFBT Role "Test Execution" string constant.
     */
    public static final String TEST_EXECUTION = "PP_CFBT_TestExecution";

    /**
     * CFBT Role "Test Management" string constant.
     */
    public static final String TEST_MANAGEMENT = "PP_CFBT_TestManagement";

    /**
     * CFBT Role "Financial Reporting" string constant.
     */
    public static final String FINANCIAL_REPORTING = "PP_CFBT_FinancialReporting";

    /**
     * CFBT Role "Administrator" string constant.
     */
    public static final String ADMINISTRATOR = "PP_CFBT_Administrator";

    private static final List<String> CFBT_ROLES = new ArrayList<String>() {{
        add(TECHNICAL_RESULTS);
        add(TEST_EXECUTION);
        add(TEST_MANAGEMENT);
        add(FINANCIAL_REPORTING);
        add(ADMINISTRATOR);
    }};
        
    /*
     * TODO Inject this dependency.
     */
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructor
     * 
     * TODO Make private; inject instances into CFBTService, etc.
     */
    public RBAC() {
    }

    /**
     * Determines if the user has the specified role.
     * 
     * @param userInfo object containing the roles for which the user is authorized.
     * @param role the subject role
     * @return true if the user has the specified role; otherwise, false.
     * @throws SecurityException if any parameter is invalid or null 
     */
    public boolean hasRole(String userInfo, String role) throws SecurityException {
        if (userInfo == null || role == null || !(CFBT_ROLES.contains(role))) {
            /* CFBT RBAC is secondary to access control implemented by nodeweb. 
             * Therefore, null parameters can only be received in 
             * development/testing invocations.
             * Allow nulls to be processed as users without any roles.
             */
            return false;
        }    
        boolean userHasRole = false;
        try {
            UserInfo userInfoObj = mapper.readValue(userInfo, UserInfo.class);
            userHasRole = userInfoObj.getScopes().contains(role);
        } catch (Exception e) {
            // Eat the exception for now.
            e.printStackTrace();
            CFBTLogger.logInfo(logger, CFBTLogger.CalEventEnum.TEST_RUN, 
                "CFBT API: TestRepository implementation is unable to parse user info");
        }
        return userHasRole;
    }

    /** CONVENIENCE METHOD
     * For a given user and @code{Test} object, 
     * secure the test object based on the user's roles.
     * 
     * @param userInfo the @code{UserInfo} object containing the user's roles
     * @param test the @code{Test} object to process
     * @return the processed @code{Test} object.
     */
    public Test secureTestData(String userInfo, Test test) {
       
        List<Test> tests = new ArrayList<Test>();
        tests.add(test);
        secureTestData(userInfo, tests);        
        return tests.get(0);
    }
    
    /**
     * For a given user and list of @code{Test} objects, 
     * secure the test objects based on the user's roles.
     * 
     * @param userInfo the @code{UserInfo} object containing the user's roles
     * @param tests the list of @code{Test} objects to process
     * @return the processed list of @code{Test} objects.
     */
    public List<Test> secureTestData(String userInfo, List<Test> tests) {
       
        secureTestManagement(userInfo, tests);
        /* TODO Add access control for the remaining roles as follows:
         *      secureTestExecution(userInfo, tests);
         *      secureTechnicalResults(userInfo, tests);
         *      secureFinancialReporting(userInfo, tests);
         *      secureAdministrator(userInfo, tests);
         */
        
        return tests;
    }
    
    /**
     * For a given user and list of @code{Test} objects, 
     * secure the test data based on the TEST_MANAGEMENT role.
     * 
     * @param userInfo the @code{UserInfo} object containing the user's roles
     * @param tests the list of @code{Test} objects to process
     * @return the processed list of @code{Test} objects.
     */
    public List<Test> secureTestManagement(String userInfo, List<Test> tests) {
        if (!hasRole(userInfo, TEST_MANAGEMENT)) {
            // Redact secure parameters.
            for (Test test : tests) {
                redactSecureParameters(test);
            }
        }
        return tests;
    }

    /*
     * Redact all 'secure' parameters in the specified @code{Test} object.
     * 
     * @param test the @code{Test} object to process
     * @return the processed @code{Test} object.
     */
    private void redactSecureParameters(Test test) {
        for (Parameter parameter : test.getParameters()) {
            if (parameter.getSecure() != null && parameter.getSecure() && parameter.getValue() != null && parameter.getValue().length > 0) {
                parameter.setValue("REDACTED".toCharArray());
            }
        }
    }

}