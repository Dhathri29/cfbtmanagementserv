
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.sre.cfbt.data.CFBTData;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

/**
 * Holds a list of execution requests.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "tests"
})
public class ExecutionRequestList extends CFBTData {

    /**
     * Execution Request List id.
     * (Required)
     * 
     */
    @JsonProperty("id")
    private String id;
    /**
     * A way to pass a collection of execution requests through a REST API
     * 
     */
    @JsonProperty("tests")
    @Valid
    private List<ExecutionRequest> requests = new ArrayList<ExecutionRequest>();
   
    public ExecutionRequestList(final String id, final List<ExecutionRequest> requests) {
        this.id = id;
        this.requests = requests;
    }

    /**
     * Execution Request List id.
     * (Required)
     */
    @NotNull
    public String getId() {
        return this.id;
    }

    /**
     * A way to pass a collection of execution requests through a REST API
     * @return {@link ExecutionRequest list}
     */
    @NotNull
    public List<ExecutionRequest> getRequests() {
        return this.requests;
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
