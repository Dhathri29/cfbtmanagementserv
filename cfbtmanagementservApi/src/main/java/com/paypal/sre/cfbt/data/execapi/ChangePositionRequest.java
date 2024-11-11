/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.data.execapi;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Holds ChangePosition
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "positionOffset"
})
public class ChangePositionRequest {
    /**
     * By how much to change the current position, negative numbers move it down the queue.
     * (Required)
     */
    @JsonProperty("positionOffset")
    @NotNull
    private int positionOffset;
    
    public ChangePositionRequest(int positionOffset) {
        this.positionOffset = positionOffset;
    }
    
    public ChangePositionRequest() { }
    
    /**
     * By how much to change the current position, negative numbers move it down the queue.
     * (Required)
     */
    @JsonProperty("positionOffset")
    public int getPositionOffset() {
        return positionOffset;
    }
    
    /**
     * By how much to change the current position, negative numbers move it down the queue.
     * (Required)
     */
    @JsonProperty("positionOffset")
    public void setPositionOffset(int positionOffset) {
        this.positionOffset = positionOffset;
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
