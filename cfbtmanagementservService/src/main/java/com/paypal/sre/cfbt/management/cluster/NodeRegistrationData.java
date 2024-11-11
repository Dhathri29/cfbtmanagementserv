/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.cluster;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import com.paypal.sre.cfbt.data.NodeDetails;
import com.paypal.sre.cfbt.data.NodeStatusData;
import com.paypal.sre.cfbt.data.test.TestPackage;
import java.util.ArrayList;
import java.util.List;

/**
 * Store IP addresses in Mongo for cluster discovery, this is the data.
 *
 */
public class NodeRegistrationData {

    private String id = null;
    private String IP = null;
    private String registeredTime = null;
    private Boolean active;
    private Integer numConfiguredThreads = null;
    private NodeStatusData nodeStatusData;
    private NodeDetails.NodeType nodeType;
    private List<TestPackage> packages = new ArrayList<>();
    private Boolean enableTestRun = true;

    public String getIP() {
        return IP;
    }

    public String getRegisteredTime() {
        return registeredTime;
    }

    public Integer getNumConfiguredThreads() {
        return numConfiguredThreads;
    }

    public String getId() {
        return id;
    }

    public Boolean getActive() {
        return active;
    }


    public NodeStatusData getNodeStatusData() {
        return nodeStatusData;
    }

    public NodeDetails.NodeType getNodeType() {
        return nodeType;
    }

    public List<TestPackage> getPackages() {
        return packages;
    }

    public void setIP(String IP) {
        this.IP = IP;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRegisteredTime(String registeredTime) {
        this.registeredTime = registeredTime;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setNumConfiguredThreads(int numConfiguredThreads) {
        this.numConfiguredThreads = numConfiguredThreads;
    }

    public void setNodeStatusData(NodeStatusData nodeStatusData) {
        this.nodeStatusData = nodeStatusData;
    }

    public void setNodeType(NodeDetails.NodeType nodeType) {
        this.nodeType = nodeType;
    }
    
    public void setPackages(List<TestPackage> packages) {
        this.packages = packages;
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
    public Boolean getEnableTestRun() {
        return enableTestRun;
    }
    
    public void setEnableTestRun(Boolean enableTestRun) {
        this.enableTestRun = enableTestRun;
    }

}
