
package com.paypal.sre.cfbt.data.execapi;

import javax.annotation.Generated;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds details of the node.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "ipAddress",
    "numberConfiguredThreads",
    "enableTestRun"
})
public class NodeUpdateProperties {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("ipAddress")
    @NotNull
    private String ipAddress;
    /**
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    private Integer numberConfiguredThreads;
    /**
     * Indicates if node is enabled or disabled for executing tests
     * (Required)
     * 
     */
    @JsonProperty("enableTestRun")
    @NotNull
    private Boolean enableTestRun;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("ipAddress")
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("ipAddress")
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    public Integer getNumberConfiguredThreads() {
        return numberConfiguredThreads;
    }

    /**
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    public void setNumberConfiguredThreads(Integer numberConfiguredThreads) {
        this.numberConfiguredThreads = numberConfiguredThreads;
    }

    /**
     * Indicates if node is enabled or disabled for executing tests
     * (Required)
     * 
     */
    @JsonProperty("enableTestRun")
    public Boolean getEnableTestRun() {
        return enableTestRun;
    }

    /**
     * Indicates if node is enabled or disabled for executing tests
     * (Required)
     * 
     */
    @JsonProperty("enableTestRun")
    public void setEnableTestRun(Boolean enableTestRun) {
        this.enableTestRun = enableTestRun;
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
