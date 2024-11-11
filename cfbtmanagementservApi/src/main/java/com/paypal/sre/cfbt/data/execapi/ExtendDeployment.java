/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.data.execapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

/**
 * Holds ExtendDeployment
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtendDeployment {
    
     /**
     * The amount of time to extend the deployment
     * (Required)
     */
    @JsonProperty("extendedTimeInSeconds")  
    @NotNull
    private int extendedTimeInSeconds = 0;
    
    public ExtendDeployment(int extendedTimeInSeconds) {
        this.extendedTimeInSeconds = extendedTimeInSeconds;
    }
    
    public ExtendDeployment() { }
    
    /**
     * The amount of time to extend the deployment
     * (Required)
     */
    @JsonProperty("extendedTimeInSeconds")
    public int getExtendedTimeInSeconds() {
        return extendedTimeInSeconds;
    }    
}

