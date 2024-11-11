/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.paypal.sre.cfbt.management.dal;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.management.cluster.NodeRegistrationData;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;

/**
 *
 */
public class NodeDetailsDAO extends AbstractDAO<NodeRegistrationData> {
      private static final NodeDetailsDAO mInstance = new NodeDetailsDAO("NodeRegistrationData");

    /**
     * Constructor of singleton instance for this object.
     * 
     * @param aCollectionName the name of the collection
     */
    public NodeDetailsDAO(String aCollectionName) {
        super(aCollectionName,NodeRegistrationData.class);
    }
    
    /**
     * Accessor for singleton instance of this object.
     * 
     * @return the instance.
     */
    public static NodeDetailsDAO getInstance() {
        return mInstance;
    }  
    
    
    /**
     * Wrapper to retrieve all IP addresses.  Retrieves all the registered nodes and returns the IP addresses of the most recent ones.
     * 
     * @param db Mongo connection factory.
     * @return List of registered IP addresses. 
     * @throws UnknownHostException Indicates that the IP address of the host could not be determined.
     */
    public Map<String, NodeRegistrationData> getAllNodes(MongoConnectionFactory db) throws UnknownHostException, Exception {
        Map<String, NodeRegistrationData> nodes = new HashMap<>();
        
        Document activeFilter = new Document("active",Boolean.TRUE);       

        try (MongoConnection c = db.newConnection()) {
            //Get all active nodes.
            List<NodeRegistrationData> allNodes = super.read(c, activeFilter);

            for (NodeRegistrationData node : allNodes) {
                 nodes.put(node.getIP(), node);
            }
        }

        return nodes;
    }
    

    /**
     * Retrieve the node details for this ip address.
     * 
     * @param db Mongo connection factory
     * @param ip IP address of this node.
     * @return Details of the node.
     * @throws Exception if problems with connection
     */
    public NodeRegistrationData getNodeDetails(MongoConnectionFactory db, String ip) throws Exception {
        try (MongoConnection c = db.newConnection()) {

            List<NodeRegistrationData> foundNode = super.read(c, new Document("ip", ip));
            
            if (foundNode.size() > 0) {
                return foundNode.get(0);
            }
            else { 
                return null;
            }            
        }
    }
    
    /**
     * This method is responsible to update the enableTestRun flag and
     * configured threads for the node.
     *
     * @param db Mongo connection factory
     * @param ipAddress The IP address of the node.
     * @param numThreads The number of configured threads that should be
     * running.
     * @param enableTestRun An enableTestRun flag that needs to be set.
     * @throws Exception In case an error occurs trying to configure the node.
     */
    public void updateEnableTestRunAndConfiguredThreads(MongoConnectionFactory db, String ipAddress, int numThreads, boolean enableTestRun) throws Exception {

        try (MongoConnection mongoConnection = db.newConnection()) {
            Document updateDocument = new Document("numConfiguredThreads", numThreads);
            updateDocument.append("enableTestRun", enableTestRun);
            Document update = new Document("$set", updateDocument);

            Document findQuery = new Document("ip", ipAddress);
            super.findAndModify(mongoConnection, findQuery, update);
        }
    }
}
