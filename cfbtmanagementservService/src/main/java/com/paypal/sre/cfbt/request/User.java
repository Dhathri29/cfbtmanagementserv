/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.management.rest.impl.RBAC;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class User {
    private static final Logger LOGGER = LoggerFactory.getLogger(User.class);
    private com.paypal.sre.cfbt.data.execapi.UserInfo mUserInfoObj = null;  
    private final RBAC rbac = new RBAC();
 
    /**
     * @param userInfo JSON representation.
     */
    public User(String userInfo) {        
        ObjectMapper mapper = new ObjectMapper();

        try {
            if (userInfo != null) {
                mUserInfoObj = mapper.readValue(userInfo, com.paypal.sre.cfbt.data.execapi.UserInfo.class);
            }
        } catch (Exception e) {
            // Eat the exception for now.
            e.printStackTrace();
            CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.USER_PARSE, "CFBT API: Unable to parse the user string");
        }
    }
    
    public String getUserId() {
        if (mUserInfoObj != null) {
            return mUserInfoObj.getUserid();
        }
        return null;
    }
    
    public List<String> getScopes(){
        if (mUserInfoObj != null) {
            return mUserInfoObj.getScopes();
        }
        return null;
    }
    
    public boolean validateUser() {
        if (StringUtils.isBlank(getUserId()) || getScopes() == null || getScopes().isEmpty()) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "UserInfo containing 'userid'and associated 'scopes' needs to be specified in the header. ", null);
            return false;
        }
        return true;
    }
    
    public boolean validateAdminUser(String userInfo) {
        boolean isUserAdmin = true;
        if(validateUser()) {
            if (!rbac.hasRole(userInfo, RBAC.ADMINISTRATOR)) {
                isUserAdmin = false;
                CFBTLogger.logInfo(LOGGER, "validateAdminUser", "CFBT API: Requesting User must have CFBT administrator role");
                CFBTExceptionUtil.throwBusinessException(CommonError.UNAUTHORIZED,
                        "User must be CFBT Administrator", null);
            }
            isUserAdmin = true;
        }
        else {
            isUserAdmin = false;
        }
        return isUserAdmin;
    }
}
