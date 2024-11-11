
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.sre.cfbt.data.test.Component;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds a list of components.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "components",
    "timestamp"
})
public class ComponentList {

    /**
     * Component list id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    @NotNull
    private String id;
    /**
     * A way to pass a collection of components through a REST API
     * 
     */
    @JsonProperty("components")
    @Valid
    private List<Component> components = new ArrayList<Component>();
    /**
     * The version of the manifest currently live
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    @NotNull
    private String timestamp;

    /**
     * Component list id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * Component list id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    /**
     * A way to pass a collection of components through a REST API
     * 
     */
    @JsonProperty("components")
    public List<Component> getComponents() {
        return components;
    }

    /**
     * A way to pass a collection of components through a REST API
     * 
     */
    @JsonProperty("components")
    public void setComponents(List<Component> components) {
        this.components = components;
    }

    /**
     * The version of the manifest currently live
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * The version of the manifest currently live
     * (Required)
     * 
     */
    @JsonProperty("timestamp")
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
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
