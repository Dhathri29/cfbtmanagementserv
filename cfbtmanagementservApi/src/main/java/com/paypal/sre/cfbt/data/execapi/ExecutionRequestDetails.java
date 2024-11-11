
package com.paypal.sre.cfbt.data.execapi;

import javax.annotation.Generated;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Details of an execution request.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({ "executionRequestId" })
public class ExecutionRequestDetails {

    /**
     * Id of execution request. (Required)
     * 
     */
    @JsonProperty("executionRequestId")
    @NotNull
    private String executionRequestId;

    /**
     * Id of execution request. (Required)
     * 
     */
    @JsonProperty("executionRequestId")
    public String getExecutionRequestId() {
        return executionRequestId;
    }

    /**
     * Id of execution request. (Required)
     * 
     */
    @JsonProperty("executionRequestId")
    public void setExecutionRequestId(String executionRequestId) {
        this.executionRequestId = executionRequestId;
    }

}