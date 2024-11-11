/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.web;

import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.execapi.Datacenter;
import com.paypal.sre.cfbt.data.execapi.Datacenters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.paypal.sre.cfbt.data.proxy.DatacentersControlMessage;
import com.paypal.sre.cfbt.management.dal.DatacenterConfigRepository;
import com.paypal.sre.cfbt.management.kafka.DataCenterProxyMessageProducer;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import java.util.List;

/**
 * This class is responsible for publishing the message in case of update in datacenter proxy
 * 
 */
public class DatacenterProxyManager{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatacenterProxyManager.class);
    /**
     * This method is responsible for publishing the datacenter proxy control message to kafka , so all other nodes will be notified.
     */
    public void requestUpdateProxy(){
      //publish message
        CFBTLogger.logInfo(LOGGER, DatacenterProxyManager.class.getCanonicalName(), "requestUpdateProxy");
        DatacentersControlMessage dataCenterProxyControlMessage = new DatacentersControlMessage(DatacentersControlMessage.MessageType.MODIFY_DATA_CENTERS);
        DataCenterProxyMessageProducer.publish(dataCenterProxyControlMessage);
        CFBTLogger.logInfo(LOGGER, CFBTLogger.CalEventEnum.UPDATE_DATACENTERS, "CFBT - Published Datacenter Proxy Control Message");
    }

    /**
     * Retrieve current datacenters..
     * @param db
     * @return 
     */
    public Datacenters getDatacenters(MongoConnectionFactory db) {
        Datacenters datacenters = new Datacenters();
        DatacenterConfigRepository datacenterConfigRepo = new DatacenterConfigRepository();
        try {
            List<Datacenter> datacenterList = datacenterConfigRepo.getDatacenters(db);
            datacenters.setDatacenters(datacenterList);
        } catch (Exception ex) {
            CFBTLogger.logError(CFBTLogger.CalEventEnum.GET_DATACENTERS, "Exception while retriving the datacenters ", ex);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Exception while retriving " + "the datacenters ", ex);
        }
        return datacenters;
    }
}
