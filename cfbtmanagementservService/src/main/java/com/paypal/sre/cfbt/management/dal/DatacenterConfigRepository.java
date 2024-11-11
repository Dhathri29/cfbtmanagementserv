/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.paypal.sre.cfbt.management.dal;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;
import org.bson.types.ObjectId;

import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.execapi.Datacenter;
import com.paypal.sre.cfbt.data.execapi.DatacenterHostMapEntry;
import com.paypal.sre.cfbt.data.execapi.Datacenters;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;


/**
 * This Repository tracks state on updating tests on startup to prevent multiple updates at the same time.
 * 
 * If the collection doesn't exist, insert first and update status, if not
 */
public class DatacenterConfigRepository {
    private final DatacenterConfigDAO datacenterDAO = DatacenterConfigDAO.getInstance();    

    /**
     * Mark the TestProcessing collection with the intent to process tests, atomically.
     * 
     * @param db Mongo connection
     * @return {@code true} if it's ok to go ahead and process tests; otherwise, {@code false}.
     * @throws UnknownHostException Indicates that the IP address of the host could not be determined.
     * @throws Exception Indicates a general exception.
     */
    public List<Datacenter> getDatacenters(MongoConnectionFactory db) throws Exception {
        
        try (MongoConnection c = db.newConnection()) {
            return datacenterDAO.read(c, null);
        }
    }   
    
    /**
     * This method is responsible for validating the Datacenters passed for updating the DataCenter collection
     * @param datacenters - Datacenters object.
     */
    public void validateDataCenter(Datacenters datacenters) {
        if (datacenters == null || datacenters.getDatacenters() == null) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Datacenter cannot be null", null);
        }
        if (datacenters.getDatacenters().size() == 0) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Datacenter cannot be empty", null);
        }
        List<Integer> uniquePorts = new ArrayList<>();
        for (Datacenter datacenter : datacenters.getDatacenters()) {
            //Need to make sure for all the datacenters the proxy ports are unique
            if(uniquePorts.contains(datacenter.getProxyPort())){
                CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Proxy port already in use", null);
            }else{
                uniquePorts.add(datacenter.getProxyPort());
            }
            for (DatacenterHostMapEntry datacenterHostMap : datacenter.getHostMap()) {
                //Need to have Host and Ip to be specified
                if (datacenterHostMap.getHost() == null || datacenterHostMap.getIp() == null) {
                    CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Either of host/ip is not present", null);
                }
                if(datacenterHostMap.getPort() == null || datacenterHostMap.getPort() == 0){
                    datacenterHostMap.setPort(443);
                }
                //Check whether the ip adress is valid 
                boolean valid = isValidIp(datacenterHostMap.getIp());
                if (!valid) {
                    CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR, "Invalid ip address in the host", null);
                }
            }
        }
    }
    /**
     * This method is responsible for validating the ip address
     * @param ip - Specified ip address
     * @return whether it is valid or not 
     */
    private boolean isValidIp(final String ip) {
        return InetAddressUtils.isIPv4Address(ip) || InetAddressUtils.isIPv6Address(ip);
    }
    
    
    /**
     * This method is responsible for updating the DataCenter collection.
     * Please note It updates/inserts the documents and all other extra documents will be deleted from the collection
     * @param db - MongoConnectionFactory object
     * @param datacenters - DataCenters object to be updated
     * @return Datacenters object
     * @throws Exception Exceptions could be thrown when updating Mongo. 
     */
    public Datacenters updateDataCenters(MongoConnectionFactory db , Datacenters datacenters) throws Exception{
        Datacenters datacentersUpdated = new Datacenters();
        List<Datacenter> datacentersList = null;
        List<ObjectId> ids = new ArrayList<>();
        try (MongoConnection c = db.newConnection()) {
            for (Datacenter dc : datacenters.getDatacenters()){
                String id = datacenterDAO.updateDatacenter(c, dc);
                if(id != null){
                    ObjectId objectId = new ObjectId(id);
                    ids.add(objectId);
                }
            }
            //Once everything is done delete the extra Datacenters
            datacenterDAO.deleteExcludingIds(c, ids);
            //find get the Datacenters 
            datacentersList = datacenterDAO.read(c, null);
            datacentersUpdated.setDatacenters(datacentersList);
        }
        return datacentersUpdated;
    }

    /**
     * Get the release vetting datacenter's name.
     *
     * @param db The {@link MongoConnectionFactory} object
     * @return The release vetting datacenter's name.
     * @throws Exception On error while getting the default datacenter from the DB.
     */
    public String getReleaseVettingDatacenterName(MongoConnectionFactory db) throws Exception {
        Datacenter datacenter = null;
        String releaseVettingDatacenterName = null;
        try (MongoConnection mongoConnection = db.newConnection()) {
            datacenter = datacenterDAO.getReleaseVettingDatacenter(mongoConnection);
        }
        if (datacenter != null) {
            releaseVettingDatacenterName = datacenter.getName();
        }
        return releaseVettingDatacenterName;
    }

    /**
     * Validate if provided datacenter-name is valid or not.
     *
     * @param dbConnectionFactory The {@link MongoConnectionFactory} object
     * @param datacenter          The default datacenter's name.
     * @return True if provided datacenter is valid.
     * @throws Exception On error while getting the datacenters from the DB.
     */
    public boolean validateDatacenter(MongoConnectionFactory dbConnectionFactory, String datacenter) throws Exception {
        List<Datacenter> datacenters = this.getDatacenters(dbConnectionFactory);
        List<String> datacenterNames = new ArrayList<>();
        boolean validDataCenter = false;
        if (datacenters != null && !datacenters.isEmpty()) {
            for (Datacenter dc : datacenters) {
                datacenterNames.add(dc.getName());
                if (dc.getName().equals(datacenter)) {
                    validDataCenter = true;
                    break;
                }
            }
        }
        if (!validDataCenter) {
            throw new IllegalArgumentException("'" + datacenter + "' is an invalid data center. Valid data center names : " + datacenterNames.toString());
        }
        return validDataCenter;
    }
}

