
package com.paypal.sre.cfbt.management.features;

import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import java.util.List;

/**
 * This class is responsible for storing Feature change information
 * 
 */
public class FeatureChange {
    private String featureName;
    private List<JsonPatch> jsonPatchList;
    public FeatureChange(String name, List<JsonPatch> jsonPatch){
        featureName = name;
        jsonPatchList = jsonPatch;
    }
    
    public String getFeatureName(){
        return featureName;
    }
    
    public List<JsonPatch> getPatchList(){
        return jsonPatchList;
    }
}
