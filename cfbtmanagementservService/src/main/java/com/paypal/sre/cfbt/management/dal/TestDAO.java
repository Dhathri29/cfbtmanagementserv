/*
 * (C) 2016 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.management.dal;

import com.mongodb.client.model.Projections;
import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.data.test.Parameter;
import com.paypal.sre.cfbt.dataaccess.AbstractDAO;
import com.paypal.sre.cfbt.management.rest.impl.ConfigManager;
import com.paypal.sre.cfbt.mongo.MongoConnection;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import com.paypal.sre.cfbt.mongo.MongoDataMarshaller;
import java.util.Comparator;
import java.util.TreeSet;
import org.bson.conversions.Bson;

import org.bson.types.ObjectId;
import org.codehaus.plexus.util.StringUtils;

/**
 * This class is the singleton concrete implementation of the AbstractDAO
 * class that handles {@link Test} data operations in the MongoDB 
 * "Test" collection.
 */
public class TestDAO extends AbstractDAO<Test> {
    private static TestDAO INSTANCE = null;
    private MongoConnectionFactory db;

    public static TestDAO getInstance(MongoConnectionFactory db) {
        if (INSTANCE == null) {
            INSTANCE = new TestDAO("Test", db);
        }
        return INSTANCE;
    }
    
    /**
     * Constructor of singleton instance for this object.
     * @param aCollectionName containing collection name.
     * @param db The MongoConnectionFactory
     */
    public TestDAO(String aCollectionName, MongoConnectionFactory db) {
        super(aCollectionName,Test.class);
        this.db = db;
    } 

    /**
     * Load all ID's provided as long as they are both enabled and configured.
     * 
     * @param testIds The list of provided test ids.
     * @param datacenter The datacenter 
     * @return A list of {@link Test} tests that are loaded and configured.
     * @throws Exception 
     */
    public List<Test> loadEnabledConfiguredTests(List<String> testIds, String datacenter) throws Exception {
        List<Document> orFilter = new ArrayList<>();
        List<Document> enabledAndConfiguredFilter = new ArrayList<>();

        testIds.forEach((id) -> {
            orFilter.add(new Document("_id", new ObjectId(id)));
        });

        enabledAndConfiguredFilter.add(new Document("enabled", true));
        enabledAndConfiguredFilter.add(new Document("needToBeConfigured", false));
        enabledAndConfiguredFilter.add(new Document("testConfigurations." + datacenter + ".enabled", true));
        enabledAndConfiguredFilter.add(new Document("$or", orFilter));

        List<Test> tests = new ArrayList<>();    
        try (MongoConnection c = db.newConnection()) {
            tests.addAll(super.read(c, new Document("$and", enabledAndConfiguredFilter)));
        }
        
        return tests;
    }

    public void insert(Test test) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            String id = super.insert(c, test);
            test.setId(id);
        }
    }
    
    /**
     * Reads from Test data collection in MongoDB that match filter specified in {@code filter}.
     * @param c a valid MongoConnection object.
     * @param aId String with id for collection record.
     * @param loadParameters True, load the parameters with the test.
     * @return Test with specified object id, or null if it is not found.
     */   
    public Test readOne(MongoConnection c,String aId, boolean loadParameters, String parameterGroup) {
        List<Parameter> theParams = null;
        Test obj = super.readOne(c,aId);
        
        if (obj != null && loadParameters) { 
            theParams = ConfigManager.getTestResourceServClient().loadParameters(aId, parameterGroup);
            if (obj.getParameters().size()>0) {
                mergeTestParameterValues(obj,theParams);
            } else {
                //handle case where params aren't set yet in Test.
                obj.setParameters(theParams); 
                List<Parameter> temp=stripTestParameterValues(obj);
                this.update(c, obj.getId(),
                        new Document("$set", new Document("parameters", MongoDataMarshaller.encode(theParams))));
                mergeTestParameterValues(obj,temp);
            }
        } 
        
        return obj;
    }

    /**
     * Strips the parameter values from Test - used when writing to unsecured DB (e.g. Mongo)
     * @param aTest a Test to strip values from.
     * @return List of parameters stripped from Test.
     */   
    public List<Parameter> stripTestParameterValues (Test aTest) {
        List<Parameter> outParams = new ArrayList<Parameter>();
        for (Parameter aParam : aTest.getParameters()) {
            //copy into new parameter, add to out list
            Parameter newParam = new Parameter();
            newParam.setName(aParam.getName());
            newParam.setSecure(aParam.getSecure());
            newParam.setShared(aParam.getShared());
            newParam.setType(aParam.getType());
            newParam.setValue(aParam.getValue());
            outParams.add(newParam);
            //clear value of original
            aParam.setValue(null);
        }
        
        return outParams;
    }
    
    /**
     * Merges the parameter values from a passed list into a Test
     * used when reading from secure parameter store
     * @param aTest a Test to strip values from.
     * @param theParameters List of parameters to merge into Test.
     */
    public void mergeTestParameterValues (Test aTest,  List<Parameter> theParameters) {
        for (Parameter mergeParam : theParameters) {
            for (Parameter testParam : aTest.getParameters()) {
                if (testParam.getName().equals(mergeParam.getName())) {
                    testParam.setValue(mergeParam.getValue());
                }
            }
        }
    }

    public List<Test> getTests(List<String> testIds) throws Exception {
        if (testIds == null) {
            throw new IllegalArgumentException("The supplied list of test id's is null");
        }

        List<Test> testList = new ArrayList<>();

        try (MongoConnection c = db.newConnection()) {
            for (String id : testIds) {
                Test thisTest = super.readOne(c, id);
                testList.add(thisTest);
            }
        }

        return testList;
    }
    
    /**
     * Reads from Test collection in MongoDB all that match filter specified in {@code filter}.
     *
     * @param c a valid MongoConnection object.
     * @param query specification of desired tests to be read. "null" will find all tests.
     * @param sort criterion
     * @param limit the maximum number of records to read
     * @return a list of all tests found.
     * @throws java.lang.Exception Exceptions thrown by Mongo.
     */
    public List<Test> read(MongoConnection c,Bson query, Bson sort, Integer limit) throws Exception {
        List<Test> testList = super.read(c,query,null,sort,limit);
        return testList;
    }

    /**
     * Load all active tests for the datacenter that are active, installed and release vetted.
     * @param dataCenter The datacenter.
     * @return {@link Test}
     * @throws Exception
     */
    public List<Test> loadAllActiveTests(String dataCenter) throws Exception {
        try (MongoConnection c = db.newConnection()) {
            List<Document> andArray = new ArrayList<>();
            andArray.add(new Document("installed", true));
            andArray.add(new Document("enabled", true));
            andArray.add(new Document("useForReleaseVetting", true));
            andArray.add(new Document("needToBeConfigured", false));
            andArray.add(new Document("testConfigurations." + dataCenter + ".enabled", true));
            andArray.add(new Document("testConfigurations." + dataCenter + ".releaseVetting", true));

            return super.read(c, new Document("$and", andArray));
        }
    }

    /**
     * Reads from Test collection in MongoDB all that match filter specified in {@code filter}.
     *
     * @param request
     * @throws java.lang.Exception
     */
    public void loadTests(ExecutionRequest request) throws Exception {
          // We want to return the full test even if the test Id is the only this stored.
        if (request.getTests() != null) {
            List<Test> loadedTests = new ArrayList<>();
            List<Document> idArray = new ArrayList<>();

            for (Test thisTest : request.getTests()) {
                if (StringUtils.isNotBlank(thisTest.getId())) {
                    idArray.add(new Document("_id",new ObjectId(thisTest.getId())));

                }
            }

            if (!idArray.isEmpty()) {
                try (MongoConnection c = db.newConnection()) {
                    loadedTests.addAll(super.read(c, new Document("$or", idArray)));
                }

                request.setTests(loadedTests);
            }
        }
    }

    /**
     * Retrieve components covered by the CFBT tests.
     *
     * @param covered
     * @param dataCenter
     * @return
     * @throws Exception
     */
    public List<Component> getCoveredComponents(boolean covered, String dataCenter) throws Exception {
        List<Component> components = new ArrayList<>();
        try (MongoConnection c = db.newConnection()) {
            Document queryDoc = null;
            if (covered) {
                queryDoc = new Document("enabled", true);
                queryDoc.append("useForReleaseVetting", true);
                if(!org.apache.commons.lang3.StringUtils.isBlank(dataCenter)) {
                    queryDoc.append("testConfigurations."+ dataCenter+".enabled", true);
                    queryDoc.append("testConfigurations."+ dataCenter+".releaseVetting", true);
                }
                queryDoc.append("needToBeConfigured", false);
                queryDoc.append("installed", true);
            }
            Bson projection = Projections.include("components");
            List<Test> testsListData = read(c, queryDoc, projection, null, null);
            Comparator<Component> comp = (Component o1, Component o2) -> o1.getName().compareTo(o2.getName());
            TreeSet<Component> componentDistinct = new TreeSet<>(comp);
            for (Test test : testsListData) {
                if (test.getComponents() != null && test.getComponents().size() > 0) {
                    componentDistinct.addAll(test.getComponents());
                }
            }
            components.addAll(componentDistinct);
        }
        return components;
    }
}

