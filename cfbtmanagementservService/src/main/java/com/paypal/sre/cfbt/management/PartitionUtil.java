/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management;

import com.paypal.sre.cfbt.data.executor.TestExecutionContainer;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.shared.CFBTLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class deals with partitioning a collection of tests in a way that permits
 * tests which have no shared resources to be executed independently on separate threads
 * whereas tests which share one or more resources must be executed sequentially on a single
 * thread.  This contains a utility method which performs the partition method.
 * 
 * This method is a core component of the system called for by the
 * <a href="https://engineering.paypalcorp.com/confluence/display/SRE/CFBT+High+Level+Design+Document#CFBTHighLevelDesignDocument-1.2.11.2.1QueuingSystem">high-level design</a>
 * and the
 * <a href="https://engineering.paypalcorp.com/confluence/display/SRE/CFBT+Logical+Architecture#CFBTLogicalArchitecture-5.DistributedRemoteExecutionSystemArchitecture">logical architecture</a>
 * 
 * Consider some tests T1, T2, T3 as shown in this diagram.  These tests make use of
 * shared parameters "P0,P1,P2,P3".  Given this arrangement, we want to know
 * which tests can be executed concurrently and which tests must be executed sequentially
 * in order to avoid resource contention.
 * 
 * <pre>
 * 
 *     T1  T2   T3
 *    /|  / |    |
 *   / | /  |    |
 *  P0 P1   P2   P3
 * </pre>
 * 
 * In this situation, we really want to know if there is a connection between one test
 * and another.  In order to do this, we consider "T1, T2, T3" to be the "nodes" of the
 * graph we consider that T1 and T2 are "connected" by an edge in this case because
 * there is a resource (P1) which is used by both tests T1 and T2.  From this graph, we can use the well-known
 * <a href="http://jgrapht.org/javadoc/org/jgrapht/alg/ConnectivityInspector.html">connectedSets</a>
 * algorithm to determine what resources are "connected" to each other.
 * 
 * Once that is done, we can find all of the tests which are independent in this sense
 * and those tests can be executed on separate threads with separate processors whereas the
 * tests which are a part of the same connected component must be executed sequentially on
 * a single thread.
 * 
 * 
 * 
 */
public class PartitionUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartitionUtil.class);

    
    private PartitionUtil() {
    }
    
    /**
     * This method partitions the tests into tests that can be run
     * independently.  This is done through a graph-theoretic mechanism
     * where each test" is treated as a node in a graph
     * and the nodes are connected if they use the same resource.  From
     * this graph, the set of independently connected subgraphs is found, these
     * are the sets of tests which can be executed independently.
     * @param requestList A collection of tests to evaluate.
     * @return A partition of tests into ones that can be executed on separate threads.
     */
    public static List<List<TestExecutionContainer>> partitionTestsBySharedData(List<TestExecutionContainer> requestList) {

        // The resources are the "nodes" in the graph.
        
        UndirectedGraph<String, DefaultEdge> g =
            new SimpleGraph<>(DefaultEdge.class);

        Map<String, Set<String>> testsByResource = new HashMap<>();
        Map<String, TestExecutionContainer> testsById = new HashMap<>();
        
        for (TestExecutionContainer container : requestList) {
            String id = container.getTest().getId();
            testsById.put(id, container);
            
            g.addVertex(id);

            // For each shared resource, collect
            // the tests which use it together.
            List<String> r = new ArrayList<>();
            for (Parameter p : container.getTest().getParameters()) {
                if (p.getShared()) {
                    // note: because this is not decrypted at this point, it should NOT need to be stored as a char []
                    r.add(String.copyValueOf(p.getValue()));
                }
            }
            for (String res : r) {
                if (!testsByResource.containsKey(res)) {
                    testsByResource.put(res, new HashSet<>());
                }
                testsByResource.get(res).add(id);
            }
        }
        
        // For each shared resource, mark edges between
        // tests which share it.
        for (Map.Entry<String, Set<String>> e : testsByResource.entrySet()) {
            for (String t0 : e.getValue()) {
                for (String t1 : e.getValue()) {
                    if (!t0.equals(t1)) {
                        g.addEdge(t0, t1);
                    }
                }
            }
        }
        
        
        // Find the set of connected sets.  These are the
        // resources which can be handled independently.
        ConnectivityInspector<String, DefaultEdge> connectivityInspector = new ConnectivityInspector(g);
        List<Set<String>> connectedTests = connectivityInspector.connectedSets();

        // Now partition the tests according to the connected
        // resources.  This works because the tests have the
        // same connectivity as the resources because the tests
        // represent the "edges" in that graph, so if the graph
        // is connected, the edges are also connected.
        List<List<TestExecutionContainer>> partition = new ArrayList<>();
        for (Set<String> tests : connectedTests) {
            List<TestExecutionContainer> testsForResources = new ArrayList<>();
            for (String testId : tests) {
                testsForResources.add(testsById.get(testId));
            }
            partition.add(testsForResources);
        }
        
        return partition;
    }
    
    /**
     * This method does a sort in place of the test partitions.
     * First tests are sorted within each partition by priority weight,
     * then the partitions are sorted by the highest priority test within them.
     * @param partitionTests A collection of partitions of tests to sort.
     */  
    public static void sortPartitionsByPriority(List<List<TestExecutionContainer>> partitionTests) {
       
        //then sort all tests within partitions
        for (List<TestExecutionContainer> testsPartition : partitionTests) {
            //sort tests within partition by highest priority
            Collections.sort(testsPartition, new Comparator<TestExecutionContainer>(){
                public int compare(TestExecutionContainer t1, TestExecutionContainer t2){
                    if(t1.getTest().getPriorityWeight().equals(t2.getTest().getPriorityWeight()))
                        return 0;
                    return t1.getTest().getPriorityWeight() < t2.getTest().getPriorityWeight() ? -1 : 1;
                }
           });
        }
        
        //now sort partitions by highest priority (will be array index 0)
        Collections.sort(partitionTests, new Comparator<List<TestExecutionContainer>>(){
            public int compare(List<TestExecutionContainer> p1, List<TestExecutionContainer> p2){
                if(p1.get(0).getTest().getPriorityWeight().equals(p2.get(0).getTest().getPriorityWeight()))
                    return 0;
                return p1.get(0).getTest().getPriorityWeight() < p2.get(0).getTest().getPriorityWeight() ? -1 : 1;
            }
        });
       
    }
    
    /**
     * Takes in a partition based on the TestExecutionContainer and returns a partition of test id's.
     * @param partitionTests A partitioned set of TestExecutionContainers
     * @return list of partitioned test ids.
     */
    public static List<List<String>> getTestPartitions(List<List<TestExecutionContainer>> partitionTests){
        List<List<String>> testPartitions = new ArrayList<>();
        for (List<TestExecutionContainer> aRequest: partitionTests) {
            List<String> aPartition = new ArrayList<>();
            Iterator<TestExecutionContainer> iter = aRequest.iterator();
            while(iter.hasNext()){
                aPartition.add(iter.next().getTest().getId());
            }
            testPartitions.add(aPartition);
        }
        return testPartitions;
    }

    /**
     * Print the partitions to CAL and Application logs
     * @param partitionTests The filled partition. 
     */
    public static void printTestPartitions(List<List<TestExecutionContainer>> partitionTests) {
        if (partitionTests == null) {
            return;
        }

        int index = 0;

        for (List<TestExecutionContainer> partition : partitionTests) {
            CFBTLogger.logInfo(LOGGER, PartitionUtil.class.getCanonicalName(), "Partition " + index);
            
            if (partition == null) {
                continue;
            }
            
            for (TestExecutionContainer test : partition) {
                CFBTLogger.logInfo(LOGGER, PartitionUtil.class.getCanonicalName(), "Execution Id = " + test.getExecution().getId() + ", " + test.getTest().getName());
            }
            index++;
        }
    }
}
