
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * JSON to update parts of the cluster.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("com.googlecode.jsonschema2pojo")
@JsonPropertyOrder({
    "nodeProperties"
})
public class ClusterUpdateRequest {

    /**
     * For each node, the data to update
     * 
     */
    @JsonProperty("nodeProperties")
    @Valid
    private List<NodeUpdateProperties> nodeProperties = new ArrayList<NodeUpdateProperties>();

    /**
     * For each node, the data to update
     * 
     */
    @JsonProperty("nodeProperties")
    public List<NodeUpdateProperties> getNodeProperties() {
        return nodeProperties;
    }

    /**
     * For each node, the data to update
     * 
     */
    @JsonProperty("nodeProperties")
    public void setNodeProperties(List<NodeUpdateProperties> nodeProperties) {
        this.nodeProperties = nodeProperties;
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
