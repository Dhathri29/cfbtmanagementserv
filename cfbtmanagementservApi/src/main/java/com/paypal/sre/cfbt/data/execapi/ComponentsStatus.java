package com.paypal.sre.cfbt.data.execapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModelProperty;

/**
 * The status for the list of components.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentsStatus {
    
    /**
     * Whether the components has covered tests.
     */
    @JsonProperty("hasTests")
    @ApiModelProperty(required = false, value = "true if there are cfbt tests covering the list of components.")
    private boolean hasTests;

    /**
     * @return the hasTests
     */
    @JsonProperty("hasTests")
    public boolean hasTests() {
        return hasTests;
    }

    /**
     * @param hasTests the hasTests to set
     */
    @JsonProperty("hasTests")
    public void setHasTests(boolean hasTests) {
        this.hasTests = hasTests;
    }
    
    

}
