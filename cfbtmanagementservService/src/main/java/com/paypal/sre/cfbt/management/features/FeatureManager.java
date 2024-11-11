package com.paypal.sre.cfbt.management.features;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ebayinc.platform.services.jsonpatch.JSonPatchProcessor;
import com.google.common.base.Objects;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.Feature;
import com.paypal.sre.cfbt.data.features.FeatureControlMessage;
import com.paypal.sre.cfbt.management.dal.FeatureRepository;
import com.paypal.sre.cfbt.management.kafka.FeatureControlMessageProducer;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * The purpose of the class to listen the Features control message
 * 
 */
@Component
public class FeatureManager {
    private static List<Feature> features;
    FeatureRepository featureRepo = new FeatureRepository();
    private static JSonPatchProcessor jsonPatchProcessor;
    @Inject 
    public FeatureManager(JSonPatchProcessor jsonPatchProcessor){
        setJsonPatchProcessor(jsonPatchProcessor);
        
    }
    private static final FeatureManager FEATURES_MANAGER = new FeatureManager();
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FeatureManager.class);
    
    protected FeatureManager(){
        setFeatures(new ArrayList<>());
    }
    
   
    
    /**
     * This function is responsible for changing the Feature
     * @param featureChange  
     * @param userId
     * @return updated feature
     * @throws Exception 
     */
    public Feature changeFeature(MongoConnectionFactory db, FeatureChange featureChange, String userId) throws Exception{
        /*
         * Input validation
         */
        if(featureChange == null) {
            Exception ex = new Exception("featureChange was null.");
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Null parameter in validation.", ex );
        }
        if(userId == null) {
            Exception ex = new Exception("userId was null.");
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Null parameter in validation.", ex );
        }

        Feature feature = null;
        FeatureRepository featuresRepository = new FeatureRepository();
        feature = featuresRepository.updateFeature(db, userId, featureChange.getFeatureName(), featureChange.getPatchList(), 
                jsonPatchProcessor);
        requestUpdateFeature();
        return feature;
    }
    /**
     * Return the instance of the FeatureManager.
     * @return FeaturesManager instance.
     */
    public static FeatureManager instance() {
        return FEATURES_MANAGER;
    }
    
    
    /**
     * This method is responsible for checking whether a particular feature is wired on or not.
     * @param db - MongoConnectionFactory used to query the DB
     * @param featureName - Name of the feature.
     * @return true or false depending on wired on or wired off.
     */
    public boolean checkFeatureOn(MongoConnectionFactory db, String featureName){
        Feature feature = null;
        try {
            feature = featureRepo.getFeatureByName(db, featureName);
        } catch (Exception ex) {
            //eat the exception and send the enabled as false
            CFBTLogger.logError(LOGGER, FeatureManager.class.toString(), ex.getLocalizedMessage(), ex);
        }
        if(feature != null && feature.getEnabledFlag()) {
            return true;
        }
        return false;
    }

    /**
     * This method is responsible for getting  specific feature information from DB.
     * @param db - MongoConnectionFactory used to query the DB
     * @param featureName - Name of the feature.
     * @return The {@link Feature} object.
     */
    public Feature getFeature(MongoConnectionFactory db, String featureName){
        Feature feature = null;
        try {
            feature = featureRepo.getFeatureByName(db, featureName);
        } catch (Exception ex) {
            //eat the exception and send the enabled as false
            CFBTLogger.logError(LOGGER, FeatureManager.class.toString(), ex.getLocalizedMessage(), ex);
        }
        return feature;
    }

    /**
     * This method is responsible for publishing the message to kafka so that other nodes can list the message
     */
    public void requestUpdateFeature(){
      //publish message
        CFBTLogger.logInfo(LOGGER, FeatureManager.class.getCanonicalName(), "requestUpdateFeature");
        FeatureControlMessage featureControlMessage = new FeatureControlMessage(FeatureControlMessage.MessageType.MODIFY_FEATURES);
        FeatureControlMessageProducer.publish(featureControlMessage);
        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.UPDATE_FEATURES, "CFBT - Published Feature Control Message");
    }
    
    /**
     * This method is responsible for getting feature information from in memory features
     * @param featureName - name of the feature to search
     * @return Feature object
     */
    public Feature getFeatureInformation(String featureName){
        Feature featureReturned = null;
        for(Feature feature : features){
            if(Objects.equal(feature.getName(), featureName)){
                featureReturned = feature;
            }
        }
        return featureReturned;
    }

    /**
     * @param features the features to set
     */
    public static void setFeatures(List<Feature> features) {
        FeatureManager.features = features;
    }
    
    /**
     * @param jsonPatchProcessor the jsonPatchProcessor to set
     */
    public static void setJsonPatchProcessor(JSonPatchProcessor jsonPatchProcessor) {
        FeatureManager.jsonPatchProcessor = jsonPatchProcessor;
    }
    
}
