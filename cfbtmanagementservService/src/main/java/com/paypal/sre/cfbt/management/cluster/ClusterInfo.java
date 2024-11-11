/*
 * (C) 2017 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paypal.sre.cfbt.management.appproperty.ApplicationPropertiesInfo;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;

import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.NodeStatusData;
import com.paypal.sre.cfbt.data.ThreadDetails;
import com.paypal.sre.cfbt.data.execapi.ClusterDetails;
import com.paypal.sre.cfbt.data.execapi.NodeDetails;
import com.paypal.sre.cfbt.data.test.TestPackage;
import com.paypal.sre.cfbt.management.dal.NodeDetailsDAO;
import com.paypal.sre.cfbt.management.dal.ThreadStatistics;
import com.paypal.sre.cfbt.management.dal.ThreadStatistics.ThreadStatistic;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import com.paypal.sre.cfbt.shared.CFBTLogger;
import com.paypal.sre.cfbt.shared.CFBTLogger.CalEventEnum;
import com.paypal.sre.cfbt.shared.DateUtil;

/**
 * Retrieve the full status of each node within the cluster.
 */
public class ClusterInfo {
    public static class Node {
        private final String ip;
        private final String hostName;
        private final Integer numThreads;
        private final NodeDetails.NodeType nodeType;
        private final NodeStatusData nodeStatusData;
        private final List<ThreadStatistic> threadList = new ArrayList<>();
        private final Boolean active;
        private List<TestPackage> packages = new ArrayList<>();
        private final Boolean enableTestRun;
        
        public Node(String ip, String hostName, int numThreads,
                List<ThreadStatistic> threadList, NodeDetails.NodeType nodeType, NodeStatusData nodeStatusData, Boolean active, List<TestPackage> packages, Boolean enableTestRun) {
            this.ip = ip;
            this.hostName = hostName;
            this.numThreads = numThreads;
            this.nodeType = nodeType;
            this.nodeStatusData = nodeStatusData;
            this.active = active;
            this.packages.addAll(packages);
            if (threadList != null) {
                this.threadList.addAll(threadList);
            }
            this.enableTestRun = enableTestRun;
        }
        
        public Node(String ip, String hostName, int numThreads,
                List<ThreadStatistic> threadList, NodeDetails.NodeType nodeType, NodeStatusData nodeStatusData, Boolean active, List<TestPackage> packages) {
            this(ip, hostName, numThreads, threadList, nodeType, nodeStatusData, active, packages, null);
        }
     
        public String getIP() { return ip; }
        public String getHostName() { return hostName; }
        public Integer getNumThreads() { return numThreads; }
        public NodeDetails.NodeType getNodeType() { return nodeType; } 
        public NodeStatusData getNodeStatusData() { return nodeStatusData; }
        public Boolean getActive() { return active; }
        public List<ThreadStatistic> getThreadList() { return threadList; }
        public List<TestPackage> getPackages() { return this.packages; }
        public Boolean getEnableTestRun() { return enableTestRun; }
    }

    private final Map<String, Node> theCluster = new HashMap<>();
    private static final NodeDetailsDAO nodeDetailsDAO = NodeDetailsDAO.getInstance();

    /**
     * Pulls in nodes from both clusterDiscovery and threadStatistics and merges the results.
     * @param db Mongo Connection Factory
     * @param config The configuration needed to get configuration parameters
     * @param applicationPropertiesInfo Used to see the configuration settings
     * @throws Exception Typically mongo connection exceptions.
     */
    public ClusterInfo(MongoConnectionFactory db, Configuration config, ApplicationPropertiesInfo applicationPropertiesInfo) throws Exception {
        Map<String, NodeRegistrationData> nodeMap = nodeDetailsDAO.getAllNodes(db);  // get list of all nodes
        ThreadStatistics statsRepo = new ThreadStatistics(db, config, applicationPropertiesInfo);
        Map<String, List<ThreadStatistic>> threadStatistics = statsRepo.loadActiveNodes(); // get not dead threads
        DateTime lastGood = DateUtil.currentDateTimeUTC().minusMinutes(applicationPropertiesInfo.getNodeIsDeadInMinutes());
        // Now add to the cluster only the active nodes
        for (Map.Entry<String, NodeRegistrationData> map : nodeMap.entrySet()) {
            String ip = map.getKey();
            NodeRegistrationData nodeDetails = map.getValue();
            List<ThreadStatistic> threads = threadStatistics.get(ip);
            boolean isNodeDead = false;
            // if there are no active threads check the registered time vs the dead time
            if (threads == null || threads.size() == 0) {
                DateTime registeredDate = DateUtil.dateTimeUTC(nodeDetails.getRegisteredTime());
                int result = registeredDate.compareTo(lastGood);
                if (result < 0) {
                    isNodeDead = true;
                }
            }

            if (!isNodeDead) {
                String hostName = null;
                if (threads != null && threads.size() > 0) {
                    hostName = threads.get(0).getHostName();
                }

                NodeStatusData nodeStatusData = nodeDetails.getNodeStatusData();

                if (nodeStatusData == null) {
                    nodeStatusData = new NodeStatusData();
                }

                List<TestPackage> nodePackages = nodeDetails.getPackages();

                theCluster.put(ip, new Node(ip, hostName, nodeDetails.getNumConfiguredThreads(), threads, NodeDetails.NodeType.CFBTEXECSERV, nodeStatusData, nodeDetails.getActive(), nodePackages, nodeDetails.getEnableTestRun()));
            }
        }
    }
    
    public ClusterInfo(MongoConnectionFactory db, Configuration config) throws Exception {
        this(db, config, new ApplicationPropertiesInfo(config, db));
    }
 
    /**
     * 
     * @return Map, key is the IP address, Node is the node details.
     */
    public Map<String, Node> getFullCluster() {
        return theCluster;
    }

    /**
     * 
     * @return Map, key is the IP address, Node is the node details.
     * @throws Exception 
     */
    public void updateEnableTestRunAndConfiguredThreads(MongoConnectionFactory dbConnectionFactory, String ipAddress,
            int numOfThreads, Boolean enableTestRun) throws Exception {
        nodeDetailsDAO.updateEnableTestRunAndConfiguredThreads(dbConnectionFactory, ipAddress, numOfThreads, enableTestRun);
    }   
    
    /**
     * 
     * @return details of the cluster.
     */
    public ClusterDetails getClusterDetails() {
        ClusterDetails clusterDetails = new ClusterDetails();

        try {
                Map<String, Node> theCluster = getFullCluster();

                for (Map.Entry<String, Node> map : theCluster.entrySet()) {
                    String ip = map.getKey();
                    NodeDetails nodeDetails = new NodeDetails();
                    Node thisNode = theCluster.get(ip);
                    nodeDetails.setIpAddress(ip);
                    nodeDetails.setEnableTestRun(thisNode.getEnableTestRun());
                    nodeDetails.setPackages(thisNode.getPackages());
                    nodeDetails.setNumberConfiguredThreads(thisNode.getNumThreads());
                    NodeDetails.Status nodeStatus = NodeDetails.Status.DOWN;

                    for (ThreadStatistic threadStats : thisNode.getThreadList()) {
                        ThreadDetails threadDetail = new ThreadDetails();
                        threadDetail.setName(threadStats.getName());
                        threadDetail.setTestName(threadStats.getTestName());
                        threadDetail.setStatus(threadStats.getStatus());
                        threadDetail.setTimeStamp(threadStats.getHeartBeatDate());
                        nodeDetails.getThreads().add(threadDetail);

                        if (threadStats.getStatus() == ThreadDetails.Status.UP) {
                            nodeStatus = NodeDetails.Status.UP;
                        }

                        if (nodeDetails.getHost() == null) {
                            nodeDetails.setHost(threadStats.getHostName());
                        }
                    }
                    nodeDetails.setStatus(nodeStatus);
                    clusterDetails.getNodes().add(nodeDetails);
                }

        } catch (Exception e) {
            CFBTLogger.logError(CalEventEnum.GET_NODE_INFO,
                    "Exception during retrieving Node details from database ", e);
            CFBTExceptionUtil.throwBusinessException(CommonError.INTERNAL_SERVICE_ERROR,
                    "Error retrieving node details the request ", e);
        }
        return clusterDetails;
    }

}
