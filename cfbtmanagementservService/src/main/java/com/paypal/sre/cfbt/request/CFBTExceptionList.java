/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.paypal.sre.cfbt.request;

import com.paypal.sre.cfbt.data.execapi.ExecutionRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseRequest;
import com.paypal.sre.cfbt.data.execapi.ReleaseTest;
import com.paypal.sre.cfbt.data.execapi.Test;
import com.paypal.sre.cfbt.data.test.Component;
import com.paypal.sre.cfbt.management.appproperty.ApplicationProperty;
import com.paypal.sre.cfbt.management.dal.ApplicationPropertyDAO;
import com.paypal.sre.cfbt.management.dal.TestDAO;
import com.paypal.sre.cfbt.mongo.MongoConnectionFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class CFBTExceptionList {
    private final MongoConnectionFactory db;
    private List<String> exemptList;
    private List<String> allTestsList;

    /**
     * Default public constructor
     * @param db - Connection to Mongo.
     * @throws Exception - Mongo connection issues.
     */
    public CFBTExceptionList(MongoConnectionFactory db) throws Exception {
        this.db = db;
        ApplicationPropertyDAO appDAO = ApplicationPropertyDAO.getInstance();
        ApplicationProperty property = appDAO.getApplicationProperty(db.newConnection());
        String exemptComponents = property.getExemptComponents();
        String allTestsComponents = property.getAllTestsComponents();
        if (exemptComponents != null) {
            exemptList = Arrays.asList(exemptComponents.split(","));
        } else {
            exemptList = new ArrayList<String>();
        }
        if (allTestsComponents != null) {
            allTestsList = Arrays.asList(allTestsComponents.split(","));
        } else {
            allTestsList = new ArrayList<String>();
        }
    }

    /**
     * Updates the request based on the Exempt Components and All-Tests Components lists.
     */
    public void filter(ExecutionRequest request) throws Exception {
        boolean hasAllTests = filterAllTests(request);
        if (!hasAllTests && !request.checkFastPass()) {
            filterExempt(request);
        }
    }

    /**
     * If the request is on the exempt list, make it a deploy only request.
     * A release is exempt if all its components that have tests are on the exempt list.
     *
     * @param request {@link ExecutionRequest}
     */
    private void filterExempt(ExecutionRequest request) {
        if (request != null) {
            List<Test> testList = request.getTests();
            // if there are no tests, then don't check for exemptions
            if (testList == null || testList.size() == 0) {
                return;
            }
            ReleaseTest release = request.getReleaseTest();
            if (release != null) {
                // get a list of non-exempt tests
                List<Test> nonExemptTestList = new ArrayList<Test>();
                for (Test test : testList) {
                    List<Component> testComponents = test.getComponents();
                    if (testComponents != null) {
                        for (Component testComponent : testComponents) {
                            // if any test's component is in the release and not on the exempt list
                            if (componentInRelease(testComponent, release) && !componentOnList(testComponent, exemptList)) {
                                // then the test is considered non-exempt
                                nonExemptTestList.add(test);
                                break;
                            }
                        }
                    }
                }
                // if any exempt tests were removed
                if (nonExemptTestList.size() < testList.size()) {
                    // update the release to only have the non-exempt tests
                    request.setTests(nonExemptTestList);
                    request.setNumberTests(nonExemptTestList.size());
                    // if there are no non-exempt tests
                    if (nonExemptTestList.size() == 0) {
                        // then all the tests are exempt and the release should be marked as exempt
                        release.setDeployOnly(Boolean.TRUE);
                        release.setDeployReason(ReleaseRequest.DeployReason.BYPASS_COMPONENT.getDeployReason());
                    }
                }
            }
        }
    }

    /**
     *
     * @param request
     * @throws Exception
     * @return
     */
    private boolean filterAllTests(ExecutionRequest request) throws Exception {
        if (request != null) {
            ReleaseTest release = request.getReleaseTest();
            if (release != null) {
                String dataCenter = request.getDatacenter();
                List<Component> components = release.getComponents();
                for (Component component : components) {
                    boolean onAllTestsList = componentOnList(component, allTestsList);
                    // if any component is on the all-tests list
                    if (onAllTestsList) {
                        // then add all the active tests
                        TestDAO dao = TestDAO.getInstance(db);
                        List<Test> allActiveTests = dao.loadAllActiveTests(dataCenter);
                        request.setTests(allActiveTests);
                        request.setNumberTests(allActiveTests.size());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Remove all components in the exempt list from the list.
     *
     * @param components
     */
    public void removeExempted(List<String> components) {
        List<String> exempted = new ArrayList<>();
        exemptList.forEach((exemptComponent) -> {
            for (String component : components) {
                if (component.compareToIgnoreCase(exemptComponent) == 0) {
                    exempted.add(component);
                }
            }
        });
        components.removeAll(exempted);
    }

    public boolean isAllTests(List<String> components) {
        for (String allTestsComponent : allTestsList) {
             for (String component : components) {
                if (component.compareToIgnoreCase(allTestsComponent) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the component is in the component list.
     * @param component
     * @param componentList
     * @return
     */
    private boolean componentOnList(Component component, List<String> componentList) {
        String componentName = component.getName();
        if (componentName != null) {
            for (String componentListName : componentList) {
                if (componentName.equalsIgnoreCase(componentListName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the component is in the release test.
     * @param component
     * @param release
     * @return
     */
    private boolean componentInRelease(Component component, ReleaseTest release) {
        String componentName = component.getName();
        if (componentName != null) {
            List<Component> releaseComponents = release.getComponents();
            for (Component releaseComponent : releaseComponents) {
                if (componentName.equalsIgnoreCase(releaseComponent.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

}
