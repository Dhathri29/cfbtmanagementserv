package com.paypal.sre.cfbt.management.dal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.data.execapi.OverrideDetails;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest.ReleaseVehicle;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * This class is responsible for performing the several override details related operations.
 */
public class OverrideDetailsRepo {

    private static OverrideDetailsDAO overrideDetailsDAO = OverrideDetailsDAO.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(OverrideDetailsRepo.class);

    /**
     * Method to get the details of an existing {@link OverrideDetails} record. If there are multiple override details
     * for the same release Id, will return the latest one.
     * 
     * @param mDB
     *            the {@link MongoConnectionFactory} instance
     * @param releaseId
     *            the release Id specific to the release.
     * @return the corresponding {@link OverrideDetails} object
     * @throws Exception
     *             - Mongo related exceptions
     */
    public OverrideDetails getOverrideDetails(MongoConnectionFactory mDB, String releaseId) throws Exception {

        OverrideDetails overrideDetails = null;

        try (MongoConnection c = mDB.newConnection()) {
            overrideDetails = overrideDetailsDAO.read(c, releaseId);
        }

        return overrideDetails;
    }
    
    /**
     * @param mDB
     *            the {@link MongoConnectionFactory} instance
     * @param componentVersion
     *            the component version that need to be used for querying the db
     * @return the corresponding {@link OverrideDetails} object
     * @throws Exception
     */
    public OverrideDetails getOverrideDetailsByComponentVersion(MongoConnectionFactory mDB, String componentVersion) throws Exception {

        OverrideDetails overrideDetails = null;

        try (MongoConnection c = mDB.newConnection()) {
            overrideDetails = overrideDetailsDAO.readByComponentVersion(c, componentVersion);
        }

/*        if (overrideDetails != null) {
            return updateServiceNowTicketDetails(overrideDetails);
        }*/

        return overrideDetails;
    }
    
    /**
     * Returns the status of the override for the specified {@link ReleaseRequest} based on the details in {@link OverrideDetails} and
     * associated ServiceNow ticket. For all turboroller based releases there will be no overrides.
     * 
     * @param mDB
     *            - the {@link MongoConnectionFactory} instance
     * @param request
     *            the {@link ReleaseRequest} for the release
     * @return the {@link OverrideDetails}
     */
    public OverrideDetails getOverrideStatus(MongoConnectionFactory mDB, ReleaseRequest request) {

        OverrideDetails.Status overrideStatus = OverrideDetails.Status.NO_OVERRIDE_REQUESTED;
        OverrideDetails overrideDetails = new OverrideDetails();
        if (ReleaseVehicle.TURBOROLLER.equals(request.getReleaseVehicle())) {
            overrideDetails.setStatus(overrideStatus);
            return overrideDetails;
        }

        try {
            overrideDetails = getOverrideDetails(mDB, request.getReleaseId());
            if (overrideDetails != null) {
                overrideStatus = OverrideDetails.Status.OVERRIDE_REQUESTED;
            } else {
                CFBTLogger.logInfo(logger, OverrideDetailsRepo.class.getCanonicalName(),
                        "No override details found for release id " + request.getReleaseId());
                overrideDetails = getOverrideDetails(mDB, request.getComponents());
                if (overrideDetails != null) {
                    overrideStatus = OverrideDetails.Status.OVERRIDE_REQUESTED;
                    CFBTLogger.logInfo(logger, OverrideDetailsRepo.class.getCanonicalName(),
                            "Status of override for " + request.getReleaseId() + " : " + overrideStatus.toString());
                } else {
                    overrideDetails = new OverrideDetails();
                    overrideDetails.setStatus(OverrideDetails.Status.NO_OVERRIDE_REQUESTED);
                    return overrideDetails;
                }
            }

            overrideStatus = OverrideDetails.Status.OVERRIDDEN;
        } catch (Exception e) {
            CFBTLogger.logError(logger, OverrideDetailsRepo.class.getCanonicalName(),
                    "Unable to determine override status of the specified release id " + request.getReleaseId(), e);
        }
        overrideDetails.setStatus(overrideStatus);

        return overrideDetails;

    }


    private OverrideDetails getOverrideDetails(MongoConnectionFactory mDB, List<Component> componentsList) throws Exception {

        OverrideDetails overrideDetails = null;
        for (Component eachComponent : componentsList) {
            overrideDetails = getOverrideDetailsByComponentVersion(mDB, eachComponent.getCurrentVersion());
            if (overrideDetails == null) {
                CFBTLogger.logInfo(logger, OverrideDetailsRepo.class.getCanonicalName(),
                        "There is no override requested for component " + eachComponent.getName() +" with version " +eachComponent.getCurrentVersion());
                return null;
            }
        }
        
        CFBTLogger.logInfo(logger, OverrideDetailsRepo.class.getCanonicalName(),
                "Override details search based on component version  : " + overrideDetails.toString());
        return overrideDetails;
    }
}
