package com.paypal.sre.cfbt.management.dal;

import com.ebayinc.platform.services.jsonpatch.JSonPatchProcessor;
import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.ComponentsUse;
import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.data.FeatureChangeHistory;
import com.paypal.sre.cfbt.data.execapi.FeatureList;
import com.paypal.sre.cfbt.dataaccess.FeatureDAO;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.DateUtil;
import com.rits.cloning.Cloner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;


/**
 * This file is responsible for performing the several feature operations.
 *
 */
@Component
public class FeatureRepository {
    private final FeatureDAO featuresDAO = FeatureDAO.getInstance();

    /**
     * This method is responsible for getting list of features depending on status and/or ComponentType
     * @param db MongoConnectionFactory Object
     * @param status - status either ACTIVE , DEPRECATED , ALL
     * @param componentType - Type of component CFBTEXECSERV,CFBTAPISERV,CFBTNODEWEB,CFBTCONFIGWATCHSERV,CFBTDATAMANSERV
     * @return FeatureList object containing List of Feature
     * @throws Exception Exception
     */
    public FeatureList getFeatures(MongoConnectionFactory db, List<String> status , String componentType) throws Exception{
        FeatureList featuresList = new FeatureList();
        try (MongoConnection c = db.newConnection()){
            if(status == null || status.isEmpty()){
                status = null;
            }
            List<Feature> features = featuresDAO.read(c, status, componentType);
            if(features != null){
                featuresList.setFeatures(features);
            }
        }
        return featuresList;
    }
    
    /**
     * This method is responsible for inserting new features into features collection.
     * @param db Db
     * @param userInfo User Info
     * @param feature Feature
     * @param jSonPatchProcessor JSON Patch Processor
     * @return inserted new feature
     * @throws Exception Exception
     */
    public Feature insertFeature(MongoConnectionFactory db, String userInfo,Feature feature, JSonPatchProcessor jSonPatchProcessor) throws Exception {
        Feature featureInserted = null;
        if (validateFeature(feature)) {
            constructFeature(feature);
            try (MongoConnection c = db.newConnection()) {
                Feature featureExist = featuresDAO.readWithName(c, feature.getName());
                if (featureExist == null) {
                    String feature_id = featuresDAO.insertNewFeature(c, feature);
                    if (feature_id == null) {
                        CFBTExceptionUtil.throwBusinessException(CommonError.SERVICE_UNAVAILABLE, "Not able insert the feature",
                                null);
                    }
                } else {
                    if (!featureExist.getComponentsUse().containsAll(feature.getComponentsUse())) {
                        List<JsonPatch> jsonPatchList = new ArrayList<>();
                        int size = 0;
                        for (ComponentsUse component : feature.getComponentsUse()) {
                            JsonPatch jsonPatchComponent = new JsonPatch();
                            jsonPatchComponent.setOp(JsonPatch.Op.ADD);
                            String indexData = Integer.toString(size);
                            jsonPatchComponent.setPath("/componentsUse/" + indexData);
                            jsonPatchComponent.setValue(component.toString());
                            jsonPatchList.add(jsonPatchComponent);
                            size++;
                        }
                        if (size > 0) {
                            if (!Feature.Status.PERMANENT.equals(featureExist.getStatus())) {
                                JsonPatch jsonPatchStatus = new JsonPatch();
                                jsonPatchStatus.setOp(JsonPatch.Op.REPLACE);
                                jsonPatchStatus.setPath("/status");
                                jsonPatchStatus.setValue(Feature.Status.ACTIVE.toString());
                                jsonPatchList.add(jsonPatchStatus);

                            }
                            updateFeature(db, userInfo, feature.getName(), jsonPatchList, jSonPatchProcessor);
                        }
                    } else {
                        CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, "Document already exists; "
                                + "use patch to update", null);
                    }
                }
                featureInserted = featuresDAO.readWithName(c, feature.getName());
            }
        }
        return featureInserted;
    }

    /**
     * This method is responsible for validating features
     * @param features - specific features document.
     * @return whether this is valid or not.
     */
    private boolean validateFeature(Feature feature){
        if(feature.getName() == null || feature.getDescription() == null || feature.getComponentsUse() == null ||
                feature.getComponentsUse().isEmpty())
        {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "feature_id or description is missing"
                    , null);
        }
        return true;
    }

    /**
     * This method is responsible for all the constructing all the mandatory fields.
     * @param features Features
     */
    private void constructFeature(Feature feature){
        if(feature.getEnabledFlag() == null){
            feature.setEnabledFlag(Boolean.FALSE);
        }
        if(feature.getStatus() == null){
            feature.setStatus(Feature.Status.ACTIVE);
        }
        feature.setLastUpdated(DateUtil.currentDateTimeISOFormat());
    }

    /**
     * This method is responsible for updating a feature
     * @param db {@link MongoConnectionFactory}
     * @param userId The id of the user.
     * @param featureName The name of the feature to update
     * @param patchList List of {@link JsonPatch}
     * @param jsonPatchProcessor {@link JSonPatchProcessor}
     * @return {@link Feature}
     * @throws java.lang.Exception On Mongo Exceptions
     */
    public Feature updateFeature(MongoConnectionFactory db, String userId, String featureName, List<JsonPatch> patchList,
            JSonPatchProcessor jsonPatchProcessor) throws Exception{
        Feature feature = null;
        //First validate Feature Patch
        validateUpdatePatch(patchList);

        try(MongoConnection c = db.newConnection()){
            //Check whether the feature exist or not.
            Feature featureExisting = featuresDAO.readWithName(c, featureName);
            if(featureExisting == null){
                CFBTExceptionUtil.throwBusinessException(CommonError.INVALID_RESOURCE_ID, "Specific feature does not "
                        +"exist " + featureName, null);
            }
            Feature featureUpdated = jsonPatchProcessor.mergeObject(patchList, featureExisting);

            //Need to make sure the componentsUse is unique
            featureUpdated.setLastUpdated(DateUtil.currentDateTimeISOFormat());
            List<ComponentsUse> componentList = featureUpdated.getComponentsUse();
            Set<ComponentsUse> componentListUnique = new TreeSet<>();
            componentListUnique.addAll(componentList);
            componentList.clear();
            componentList.addAll(componentListUnique);
            featureUpdated.setComponentsUse(componentList);

            //Setting the id as null due to mongo 2.4.8 behavior
            featureUpdated.setId(null);

            //Only update the change history if there is any change to feature flag and / or feature data
            if(!Objects.equals(featureUpdated.getFeatureData(),featureExisting.getFeatureData()) ||
                    !Objects.equals(featureUpdated.getEnabledFlag(), featureExisting.getEnabledFlag())){
                FeatureChangeHistory featureChangeHistory = new FeatureChangeHistory();
                featureChangeHistory.setPreviousEnabledFlag(featureExisting.getEnabledFlag());
                featureChangeHistory.setPreviousDateUpdated(featureExisting.getLastUpdated());
                featureChangeHistory.setPreviousFeatureData(featureExisting.getFeatureData());
                featureChangeHistory.setUserId(userId);
                featureUpdated.getChangeHistory().add(featureChangeHistory);
            }
            feature = featuresDAO.updateExistingFeature(c, featureExisting, featureUpdated);
        }
        return feature;
    }

    /**
     * This method is responsible for validating the updated feature patch
     * @param patchList - List of JsonPatch passed into for update
     * @return - Whether patch is valid or not.
     * @throws Exception Exception
     */
    private void validateUpdatePatch(List<JsonPatch> patchList) throws Exception {
        String errorDetails = "Requested operation not supported for specified field: ";
        patchList.forEach((patch) -> {
            if (patch.getOp() == JsonPatch.Op.ADD && patch.getPath().matches(
                    "(\\/)(id|featureId|enabledFlag|status|lastUpdated|changeHistory)(.*)")) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getPath(), null);
            } else if (patch.getOp() == JsonPatch.Op.REMOVE && patch.getPath().matches(
                    "(\\/)(id|featureId|lastUpdated|enabledFlag|status|changeHistory)(.*)")) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getPath(), null);
            } else if (patch.getOp() == JsonPatch.Op.REPLACE && patch.getPath().matches(
                    "(\\/)(id|featureId|lastUpdated|changeHistory)(.*)")) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getPath(), null);
            } else if (patch.getOp() == JsonPatch.Op.COPY && patch.getPath().matches(
                    "(\\/)(id|featureId|lastUpdated|enabledFlag|status|changeHistory)(.*)")) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getPath(), null);
            } else if (patch.getOp() == JsonPatch.Op.MOVE && patch.getFrom().matches(
                    "(\\/)(id|featureId|lastUpdated|enabledFlag|status|changeHistory)(.*)")) {
                CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getFrom(), null);
            }
        });
    }
    
    /**
     * This method is responsible for updating feature data
     * @param db - MongoConnectionFactory
     * @param featureName - Name of the feature
     * @param featureData - feature data in the feature document
     * @throws Exception - Mongo related exceptions
     */
    public void updateFeatureData(MongoConnectionFactory db, String featureName, String featureData) throws Exception{
        try(MongoConnection c = db.newConnection()){
            Feature featureExisting = featuresDAO.readWithName(c, featureName);
            if(featureExisting != null && !featureExisting.getFeatureData().equals(featureData))
            {
                Cloner cloner=new Cloner();
                Feature featureUpdated = cloner.deepClone(featureExisting);
                featureUpdated.setFeatureData(featureData);
                featureUpdated.setId(null);
                featuresDAO.updateExistingFeature(c, featureExisting, featureUpdated);
            }
        }
    }
    
    /**
     * This method is responsible for fetching feature document with respect to name
     * @param db - MongoConnectionFactory object
     * @param featureName - Name of the feature
     * @return Feature document
     * @throws Exception - Mongo related exceptions
     */
    public Feature getFeatureByName(MongoConnectionFactory db, String featureName) throws Exception{
        Feature feature = null;
        try(MongoConnection c = db.newConnection()){
            feature = featuresDAO.readWithName(c, featureName);
        }
        return feature;
    }
}
