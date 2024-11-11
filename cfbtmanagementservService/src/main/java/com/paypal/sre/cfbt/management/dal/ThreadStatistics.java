/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.dal;

import static com.paypal.sre.cfbt.shared.NetworkUtil.getLocalInetAddress;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paypal.sre.cfbt.management.appproperty.ApplicationPropertiesInfo;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.sre.cfbt.data.ThreadDetails;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.DateUtil;

/**
 * This class contains the higher level database operations
 * data.
 */
public class ThreadStatistics {

    private static final ThreadStatisticsDAO THREAD_STATISTICS_DAO = ThreadStatisticsDAO.getInstance();
    
    MongoConnectionFactory db;
    private NodeId nodeId;
    private int nodeIsDeadInMinutes;
    private int threadIsDownInMinutes;
    
    public static class NodeId {
        private String ip;
        private String hostName;
        
        NodeId(String ip, String hostName) {
            this.ip = ip;
            this.hostName = hostName;
        }
        
        public String getIp() {
            return ip;
        }
        
        public String getHostName() {
            return hostName;
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

    @JsonPropertyOrder({
    "ipAddress",
    "hostName",
    "name",
    "testName",
    "heartBeatDate"
    })
    public static class ThreadStatistic {
        @JsonProperty("ipAddress")
        String ip;
         
        @JsonProperty("hostName")
        String hostName;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("testName")
        private String testName;

        @JsonProperty("heartBeatDate")
        private String heartBeatDate;
        
        @JsonProperty("status")
        ThreadDetails.Status status; 
        
        public String getIp() { return ip; }
        public String getHostName() { return hostName; }
        public String getHeartBeatDate() { return heartBeatDate; }
        public ThreadDetails.Status getStatus() { return status; }
        public String getName() { return name; }
        public String getTestName() { return testName; }
        
        public ThreadStatistic() {}
        
        public ThreadStatistic(String ip, String hostName, String name, String testName, String heartBeatDate, ThreadDetails.Status status) {
            this.ip = ip;
            this.hostName = hostName;
            this.name = name;
            this.testName = testName;
            this.heartBeatDate = heartBeatDate;
            this.status = status;
        }
    } 

    /**
     * Constructor
     * @param db Connection information to Mongo.
     * @param config Requires the configuration to retrieve the IP address deterministically.
     * @throws Exception In case an error retrieving IP.
     */
    public ThreadStatistics(MongoConnectionFactory db, Configuration config, ApplicationPropertiesInfo applicationPropertiesInfo) throws Exception {
        this.db = db;

        InetAddress inet = getLocalInetAddress(config);

        nodeId = new NodeId(inet.getHostAddress(), inet.getHostName());
        nodeIsDeadInMinutes = applicationPropertiesInfo.getNodeIsDeadInMinutes();
        threadIsDownInMinutes = applicationPropertiesInfo.getThreadIsDownInMinutes();
    }
    
    /**
     *
     * @return {@link NodeId} object containing ip and hostname for the node.
     */
    public NodeId getNodeId() {
        return this.nodeId;
    }

    /**
     * The cutoff date when nodes are considered active and running.
     * @return Cutoff time before which nodes are considered not active.
     */
    private DateTime detectDeadTime() {         
        return DateUtil.currentDateTimeUTC().minusMinutes(nodeIsDeadInMinutes);       
    }

    /**
     * @return Cutoff time when node is considered down but not dead.
     */
    private DateTime detectDownTime() {         
        return DateUtil.currentDateTimeUTC().minusMinutes(threadIsDownInMinutes);       
    }

    /**
     * Load all of the active nodes.
     * 
     * @return A list of all of the active nodes, the key is the IP address.
     * @throws Exception when unable to connect to Mongo.
     */
    public Map<String, List<ThreadStatistic>> loadActiveNodes() throws Exception {  
        Map<String, List<ThreadStatistic>> aliveThreadStats = new HashMap<>();

        try (MongoConnection c = db.newConnection()) {
             List<ThreadStatistic> threadStatistics = THREAD_STATISTICS_DAO.read(c, null);
           
            for (ThreadStatistic stat : threadStatistics) {
                if (stat.heartBeatDate != null) {
                   DateTime lastDate = DateUtil.dateTimeUTC(stat.heartBeatDate);

                   if (lastDate.isAfter(detectDeadTime())) {
                        if (lastDate.isBefore(detectDownTime())) {
                            stat.status = ThreadDetails.Status.DOWN;
                        }
                       
                        if (aliveThreadStats.containsKey(stat.ip)) {
                            aliveThreadStats.get(stat.ip).add(stat);
                        }
                        else {
                            List<ThreadStatistic> newArray = new ArrayList<>();
                            newArray.add(stat);
                            aliveThreadStats.put(stat.ip, newArray);
                        }
                   }
                }
            }
        }

        return aliveThreadStats;
    }
}
 
