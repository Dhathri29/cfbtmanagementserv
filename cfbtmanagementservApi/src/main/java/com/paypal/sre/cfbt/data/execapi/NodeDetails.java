
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.paypal.sre.cfbt.data.NodeStatusData;
import com.paypal.sre.cfbt.data.ThreadDetails;
import com.paypal.sre.cfbt.data.test.TestPackage;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds details of the node.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "host",
    "ipAddress",
    "threads",
    "status",
    "numberConfiguredThreads",
    "nodeType",
    "nodeStatusData",
    "packages",
    "enableTestRun"
})
public class NodeDetails {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("host")
    @NotNull
    private String host;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("ipAddress")
    @NotNull
    private String ipAddress;
    @JsonProperty("threads")
    @Valid
    private List<ThreadDetails> threads = new ArrayList<ThreadDetails>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("status")
    @NotNull
    private NodeDetails.Status status;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    @NotNull
    private Integer numberConfiguredThreads;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("nodeType")
    @NotNull
    private NodeDetails.NodeType nodeType;
    /**
     * Stores information about NodeStatusData.
     * 
     */
    @JsonProperty("nodeStatusData")
    @Valid
    private NodeStatusData nodeStatusData;
    /**
     * The details of the test packages present in this node.
     * 
     */
    @JsonProperty("packages")
    @Valid
    private List<TestPackage> packages = new ArrayList<TestPackage>();
    /**
     * Indicates if node is enabled or disabled for executing tests
     * (Required)
     * 
     */
    @JsonProperty("enableTestRun")
    @NotNull
    private Boolean enableTestRun = true;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("host")
    public String getHost() {
        return host;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("host")
    public void setHost(String host) {
        this.host = host;
    }

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

    @JsonProperty("threads")
    public List<ThreadDetails> getThreads() {
        return threads;
    }

    @JsonProperty("threads")
    public void setThreads(List<ThreadDetails> threads) {
        this.threads = threads;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("status")
    public NodeDetails.Status getStatus() {
        return status;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("status")
    public void setStatus(NodeDetails.Status status) {
        this.status = status;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    public Integer getNumberConfiguredThreads() {
        return numberConfiguredThreads;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("numberConfiguredThreads")
    public void setNumberConfiguredThreads(Integer numberConfiguredThreads) {
        this.numberConfiguredThreads = numberConfiguredThreads;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("nodeType")
    public NodeDetails.NodeType getNodeType() {
        return nodeType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("nodeType")
    public void setNodeType(NodeDetails.NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Stores information about NodeStatusData.
     * 
     */
    @JsonProperty("nodeStatusData")
    public NodeStatusData getNodeStatusData() {
        return nodeStatusData;
    }

    /**
     * Stores information about NodeStatusData.
     * 
     */
    @JsonProperty("nodeStatusData")
    public void setNodeStatusData(NodeStatusData nodeStatusData) {
        this.nodeStatusData = nodeStatusData;
    }

    /**
     * The details of the test packages present in this node.
     * 
     */
    @JsonProperty("packages")
    public List<TestPackage> getPackages() {
        return packages;
    }

    /**
     * The details of the test packages present in this node.
     * 
     */
    @JsonProperty("packages")
    public void setPackages(List<TestPackage> packages) {
        this.packages = packages;
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

    public enum NodeType {

        CFBTEXECSERV("CFBTEXECSERV"),
        CFBTNODEWEB("CFBTNODEWEB");
        private final String value;
        private static Map<String, NodeDetails.NodeType> constants = new HashMap<String, NodeDetails.NodeType>();

        static {
            for (NodeDetails.NodeType c: NodeDetails.NodeType.values()) {
                constants.put(c.value, c);
            }
        }

        private NodeType(String value) {
            this.value = value;
        }

        @JsonValue
        @Override
        public String toString() {
            return this.value;
        }

        @JsonCreator
        public static NodeDetails.NodeType fromValue(String value) {
            NodeDetails.NodeType constant = constants.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    public enum Status {

        UP("UP"),
        DOWN("DOWN");
        private final String value;
        private static Map<String, NodeDetails.Status> constants = new HashMap<String, NodeDetails.Status>();

        static {
            for (NodeDetails.Status c: NodeDetails.Status.values()) {
                constants.put(c.value, c);
            }
        }

        private Status(String value) {
            this.value = value;
        }

        @JsonValue
        @Override
        public String toString() {
            return this.value;
        }

        @JsonCreator
        public static NodeDetails.Status fromValue(String value) {
            NodeDetails.Status constant = constants.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
