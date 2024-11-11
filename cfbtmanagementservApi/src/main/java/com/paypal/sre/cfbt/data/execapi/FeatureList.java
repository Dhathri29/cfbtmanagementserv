
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.sre.cfbt.data.CFBTData;
import com.paypal.sre.cfbt.data.Feature;


/**
 * Holds Features objects.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "features"
})
public class FeatureList extends CFBTData {

    /**
     * Array of features definitions
     * 
     */
    @JsonProperty("features")
    @Valid
    private List<Feature> features = new ArrayList<Feature>();

    /**
     * Array of features definitions
     * 
     */
    @JsonProperty("features")
    public List<Feature> getFeatures() {
        return features;
    }

    /**
     * Array of features definitions
     * 
     */
    @JsonProperty("features")
    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

}
