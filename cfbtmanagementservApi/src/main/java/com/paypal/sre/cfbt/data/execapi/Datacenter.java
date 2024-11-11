
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds details about individual datacenter configuration.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "releaseVetting",
    "restricted",
    "proxyPort",
    "hostMap",
    "internalHosts",
    "externalHosts",
    "tickets"
})
public class Datacenter {

    /**
     * Name of this datacenter
     * (Required)
     * 
     */
    @JsonProperty("name")
    @NotNull
    private String name;
    /**
     * True if this is the release vetting datacenter
     * 
     */
    @JsonProperty("releaseVetting")
    private boolean releaseVetting = false;

    /**
     * True if this is the restricted datacenter.
     *
     */
    @JsonProperty("restricted")
    private boolean restricted = false;

    /**
     * Port number for the proxy (volatile, not persisted)
     * 
     */
    @JsonProperty("proxyPort")
    private Integer proxyPort;
    /**
     * Array of host to IP address maps.
     * 
     */
    @JsonProperty("hostMap")
    @Valid
    private List<DatacenterHostMapEntry> hostMap = new ArrayList<DatacenterHostMapEntry>();
    /**
     * Array of host patterns for internal proxy hosts
     * 
     */
    @JsonProperty("internalHosts")
    private List<String> internalHosts = new ArrayList<String>();
    /**
     * Array of host patterns for external proxy hosts
     * 
     */
    @JsonProperty("externalHosts")
    private List<String> externalHosts = new ArrayList<String>();

    /**
     * Array to hold the RITM tickets
     *
     */
    @JsonProperty("tickets")
    private List<String> tickets = new ArrayList<String>();

    /**
     * Name of this datacenter
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Name of this datacenter
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * True if this is the release vetting datacenter
     * 
     */
    @JsonProperty("releaseVetting")
    public Boolean getReleaseVetting() {
        return releaseVetting;
    }

    /**
     * True if this is the release vetting datacenter
     * 
     */
    @JsonProperty("releaseVetting")
    public void setReleaseVetting(Boolean releaseVetting) {
        this.releaseVetting = releaseVetting;
    }

    /**
     * True if this is the restricted datacenter.
     *
     */
    @JsonProperty("restricted")
    public Boolean getRestricted() {
        return restricted;
    }

    /**
     * True if this is the restricted datacenter.
     *
     */
    @JsonProperty("restricted")
    public void setRestricted(Boolean restricted) {
        this.restricted = restricted;
    }

    /**
     * Port number for the proxy (volatile, not persisted)
     * 
     */
    @JsonProperty("proxyPort")
    public Integer getProxyPort() {
        return proxyPort;
    }

    /**
     * Port number for the proxy (volatile, not persisted)
     * 
     */
    @JsonProperty("proxyPort")
    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Array of host to IP address maps.
     * 
     */
    @JsonProperty("hostMap")
    public List<DatacenterHostMapEntry> getHostMap() {
        return hostMap;
    }

    /**
     * Array of host to IP address maps.
     * 
     */
    @JsonProperty("hostMap")
    public void setHostMap(List<DatacenterHostMapEntry> hostMap) {
        this.hostMap = hostMap;
    }

    /**
     * Array of host patterns for internal proxy hosts
     * 
     */
    @JsonProperty("internalHosts")
    public List<String> getInternalHosts() {
        return internalHosts;
    }

    /**
     * Array of host patterns for internal proxy hosts
     * 
     */
    @JsonProperty("internalHosts")
    public void setInternalHosts(List<String> internalHosts) {
        this.internalHosts = internalHosts;
    }

    /**
     * Array of host patterns for external proxy hosts
     * 
     */
    @JsonProperty("externalHosts")
    public List<String> getExternalHosts() {
        return externalHosts;
    }

    /**
     * Array of host patterns for external proxy hosts
     * 
     */
    @JsonProperty("externalHosts")
    public void setExternalHosts(List<String> externalHosts) {
        this.externalHosts = externalHosts;
    }

    @JsonProperty("tickets")
    public void setTickets(List<String> tickets) {
        this.tickets = tickets;
    }

    @JsonProperty("tickets")
    public List<String> getTickets() {
        return tickets;
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
