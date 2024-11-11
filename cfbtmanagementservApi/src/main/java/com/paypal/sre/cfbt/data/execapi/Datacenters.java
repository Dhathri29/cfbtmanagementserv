
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds Datacenter objects.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "datacenters"
})
public class Datacenters {

    /**
     * Array of datacenter definitions
     * 
     */
    @JsonProperty("datacenters")
    @Valid
    private List<Datacenter> datacenters = new ArrayList<Datacenter>();

    /**
     * Array of datacenter definitions
     * 
     */
    @JsonProperty("datacenters")
    public List<Datacenter> getDatacenters() {
        return datacenters;
    }

    /**
     * Array of datacenter definitions
     * 
     */
    @JsonProperty("datacenters")
    public void setDatacenters(List<Datacenter> datacenters) {
        this.datacenters = datacenters;
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
