
package com.paypal.sre.cfbt.data.execapi;

import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.sre.cfbt.data.test.TestPackage;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds details of the package action request.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "action",
    "nodeIPAddress",
    "testPackage"
})
public class NotifyPackageActionRequest {

    /**
     * The package action (DOWNLOAD or DELETE)
     * (Required)
     * 
     */
    @JsonProperty("action")
    @NotNull
    private String action;
    /**
     * The node IP address where to perform action.
     * 
     */
    @JsonProperty("nodeIPAddress")
    private String nodeIPAddress;
    /**
     * The details of the test package on which action needs to be performed
     * (Required)
     * 
     */
    @JsonProperty("testPackage")
    @NotNull
    @Valid
    private TestPackage testPackage;

    /**
     * The package action (DOWNLOAD or DELETE)
     * (Required)
     * 
     */
    @JsonProperty("action")
    public String getAction() {
        return action;
    }

    /**
     * The package action (DOWNLOAD or DELETE)
     * (Required)
     * 
     */
    @JsonProperty("action")
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * The node IP address where to perform action.
     * 
     */
    @JsonProperty("nodeIPAddress")
    public String getNodeIPAddress() {
        return nodeIPAddress;
    }

    /**
     * The node IP address where to perform action.
     * 
     */
    @JsonProperty("nodeIPAddress")
    public void setNodeIPAddress(String nodeIPAddress) {
        this.nodeIPAddress = nodeIPAddress;
    }

    /**
     * The details of the test package on which action needs to be performed
     * (Required)
     * 
     */
    @JsonProperty("testPackage")
    public TestPackage getTestPackage() {
        return testPackage;
    }

    /**
     * The details of the test package on which action needs to be performed
     * (Required)
     * 
     */
    @JsonProperty("testPackage")
    public void setTestPackage(TestPackage testPackage) {
        this.testPackage = testPackage;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

}
