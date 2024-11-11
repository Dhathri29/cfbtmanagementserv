
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds details of the cluster.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "testRequestQueueSize",
    "executionRequestQueueSize",
    "nodes"
})
public class ClusterDetails {

    /**
     * Size of the test queue.
     * (Required)
     * 
     */
    @JsonProperty("testRequestQueueSize")
    @NotNull
    private int testRequestQueueSize;
    /**
     * Size of the execution request queue.
     * (Required)
     * 
     */
    @JsonProperty("executionRequestQueueSize")
    @NotNull
    private int executionRequestQueueSize;

    @JsonProperty("nodes")
    @Valid
    private List<NodeDetails> nodes = new ArrayList<NodeDetails>();
    
    @JsonProperty("timerMapSize")
    @Valid
    private int timerMapSize;

    @JsonProperty("timerMapContents")
    @Valid
    private Set<Map.Entry<Object, Object>> timerMapContents;

    /**
     * Size of the test queue.
     * (Required)
     * 
     */
    @JsonProperty("testRequestQueueSize")
    public int getTestRequestQueueSize() {
        return testRequestQueueSize;
    }

    /**
     * Size of the test queue.
     * (Required)
     * 
     */
    @JsonProperty("testRequestQueueSize")
    public void setTestRequestQueueSize(int testRequestQueueSize) {
        this.testRequestQueueSize = testRequestQueueSize;
    }

    /**
     * Size of the execution request queue.
     * (Required)
     * 
     */
    @JsonProperty("executionRequestQueueSize")
    public Integer getExecutionRequestQueueSize() {
        return executionRequestQueueSize;
    }

    /**
     * Size of the execution request queue.
     * (Required)
     * 
     */
    @JsonProperty("executionRequestQueueSize")
    public void setExecutionRequestQueueSize(Integer executionRequestQueueSize) {
        this.executionRequestQueueSize = executionRequestQueueSize;
    }

    @JsonProperty("nodes")
    public List<NodeDetails> getNodes() {
        return nodes;
    }

    @JsonProperty("nodes")
    public void setNodes(List<NodeDetails> nodes) {
        this.nodes = nodes;
    }

    @JsonProperty("timerMapSize")
    public int getTimerMapSize() {
        return timerMapSize;
    }

    @JsonProperty("timerMapSize")
    public void setTimerMapSize(int timeMapSize) {
        this.timerMapSize = timeMapSize;
    }

    @JsonProperty("timerMapContents")
    public Set<Map.Entry<Object, Object>> getTimerMapContents() {
        return timerMapContents;
    }

    @JsonProperty("timerMapContents")
    public void setTimerMapContents(Set<Map.Entry<Object, Object>> timerMapContents) {
        this.timerMapContents = timerMapContents;
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
