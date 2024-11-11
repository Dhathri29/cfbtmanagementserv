package com.paypal.sre.cfbt.data.execapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * The request json to get the components status.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentsStatusRequest {

    /**
     * The list of components' names that are part of the release to be deployed.
     */
    @JsonProperty("components")
    @NotNull
    @ApiModelProperty(required = true, value = "The list of components' names that are part of the release to be deployed.")
    private List<String> components = new ArrayList<>();

    /**
     * The System Under Test against which the release test need to be executed.
     */
    @JsonProperty("systemUnderTest")
    @ApiModelProperty(required = false, value = "The data center against which this release test should be executed.")
    private SystemUnderTest systemUnderTest;

    /**
     * Getter to get the list of components' names
     *
     * @return The {@link List} of components' names
     */
    @JsonProperty("components")
    public List<String> getComponents() {
        return components;
    }

    /**
     * Setter to set the list of components' names
     *
     * @param components The {@link List} of components' names
     */
    @JsonProperty("components")
    public void setComponents(List<String> components) {
        this.components = components;
    }


    /**
     * Getter to get the system Under Test of the release.
     *
     * @return The {@link SystemUnderTest} object
     */
    @JsonProperty("systemUnderTest")
    public SystemUnderTest getSystemUnderTest() {
        return systemUnderTest;
    }

    /**
     * Setter to set the system Under Test of the release.
     *
     * @param systemUnderTest The {@link SystemUnderTest} object
     */
    @JsonProperty("systemUnderTest")
    public void setSystemUnderTest(SystemUnderTest systemUnderTest) {
        this.systemUnderTest = systemUnderTest;
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
