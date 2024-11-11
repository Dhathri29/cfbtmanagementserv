package com.paypal.sre.cfbt.management.appproperty;

import com.ebayinc.platform.services.jsonpatch.JsonPatch;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.data.Activity;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;
import org.apache.commons.lang.StringUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class is responsible to process JsonPatch related to Application
 * Property.
 */
public class ApplicationPropertyJsonPatchProcessor {

    private Document updateDocument = null;
    private List<Activity> activities = new ArrayList<>();
    private HashMap<String, Boolean> fieldUpdatedMap = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationPropertyJsonPatchProcessor.class);

    /**
     * This method is responsible to process JSON patch for Application Property record.
     *
     * @param userId      UserId who initiated update
     * @param appProperty - The {@link ApplicationProperty} object
     * @param patchList   - patch list
     * @throws Exception On error while processing the json patch.
     */
    public void processApplicationPropertyJson(String userId, ApplicationProperty appProperty, List<JsonPatch> patchList) throws Exception {
        String errorDetails = "Requested operation not supported for specified field: ";
        String[] newExemptComponents = null;
        String[] newAllTestsComponents = null;

        if (appProperty != null) {
            for (JsonPatch patch : patchList) {
                //The map containing field-name and respective allowed operations for that field.
                HashMap<String, List<JsonPatch.Op>> fieldOperationMap = appProperty.allowedFieldUpdateMap();

                //process JSON path and return respective field which is getting updated.
                String fieldName = this.getFieldName(fieldOperationMap, patch.getPath());

                //Check allowed operation for the field-name and validate the patch operation.
                List<JsonPatch.Op> allowedJsonOp = fieldOperationMap.get(fieldName);
                if (StringUtils.isBlank(fieldName) || allowedJsonOp == null || allowedJsonOp.isEmpty()) {
                    CFBTExceptionUtil.throwBusinessException(CommonError.FORBIDDEN, errorDetails + patch.getPath(), null);
                }
                if (!allowedJsonOp.contains(patch.getOp())) {
                    throw new IllegalArgumentException("Patch not supported, Support only " + allowedJsonOp.toString() + " operation for field - " + patch.getPath());
                }

                Object patchValue = patch.getValue();
                if (patchValue == null) {
                    throw new IllegalArgumentException("Patch cannot be applied if new value is empty for field - " + patch.getPath());
                }

                //Generate field description based on the patch-operation action & associated field description.
                HashMap<String, String> fieldDescMap = appProperty.fieldDescription();
                String fieldDescription = this.getJsonPatchAction(patch.getOp()) + fieldDescMap.get(fieldName);

                //Get the old value for the field that is being updated and also validate if new value is assignable.
                String oldValue = null;
                if (patch.getPath().matches("(\\/)(numberOfThreads)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getNumberOfThreads().toString();
                } else if (patch.getPath().matches("(\\/)(nodeIsDeadInMinutes)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getNodeIsDeadInMinutes().toString();
                } else if (patch.getPath().matches("(\\/)(threadIsDownInMinutes)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getThreadIsDownInMinutes().toString();
                } else if (patch.getPath().matches("(\\/)(mediumWaitTimeThreshold)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getMediumWaitTimeThreshold().toString();
                } else if (patch.getPath().matches("(\\/)(largeWaitTimeThreshold)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getLargeWaitTimeThreshold().toString();
                } else if (patch.getPath().matches("(\\/)(normalWaitTimeMessage)")) {
                    checkIfValidStringValue(patchValue, patch.getPath());
                    oldValue = appProperty.getNormalWaitTimeMessage();
                } else if (patch.getPath().matches("(\\/)(mediumWaitTimeMessage)")) {
                    checkIfValidStringValue(patchValue, patch.getPath());
                    oldValue = appProperty.getMediumWaitTimeMessage();
                } else if (patch.getPath().matches("(\\/)(largeWaitTimeMessage)")) {
                    checkIfValidStringValue(patchValue, patch.getPath());
                    oldValue = appProperty.getLargeWaitTimeMessage();
                } else if (patch.getPath().matches("(\\/)(exemptComponents)")) {
                    checkIfString(patchValue, patch.getPath());
                    newExemptComponents = patchValue.toString().split(",");
                    checkIfValidComponent(newExemptComponents);
                    checkIfUniqueComponent(newExemptComponents);
                    oldValue = appProperty.getExemptComponents();
                } else if (patch.getPath().matches("(\\/)(allTestsComponents)")) {
                    checkIfString(patchValue, patch.getPath());
                    newAllTestsComponents = patchValue.toString().split(",");
                    checkIfValidComponent(newAllTestsComponents);
                    checkIfUniqueComponent(newAllTestsComponents);
                    oldValue = appProperty.getAllTestsComponents();
                } else if (patch.getPath().matches("(\\/)(componentMappingThreshold)")) {
                    checkIfValidIntegerValue(patchValue, patch.getPath());
                    oldValue = appProperty.getComponentMappingThreshold().toString();
                }
                //Document that needs to be updated in DB.
                if (updateDocument == null) {
                    updateDocument = new Document(fieldName, patchValue);
                } else {
                    updateDocument.append(fieldName, patchValue);
                }
                //Mark that field was updated. This map will be used to track which fields got updated and to decide if any further action is needed.
                fieldUpdatedMap.put(fieldName, Boolean.TRUE);
                //Create activity record for this field update.
                createUpdateActivity(userId, Activity.Action.UPDATE, "ApplicationProperty", appProperty.getId(), patch.getPath(), patchValue.toString(), oldValue, fieldDescription);
            }

            checkDependencies(newExemptComponents, newAllTestsComponents, appProperty);

        }
    }

    /**
     * This method is responsible to process JSON path and return respective
     * field which is getting updated.
     *
     * @param fieldOperationMap The map containing field-name and respective
     *                          allowed operations for that field.
     * @param jsonPath          - The json path.
     * @return The field name.
     */
    private String getFieldName(HashMap<String, List<JsonPatch.Op>> fieldOperationMap, String jsonPath) {
        if (fieldOperationMap == null) {
            return null;
        }
        for (String fieldName : fieldOperationMap.keySet()) {
            if (jsonPath.matches("(\\/)(" + fieldName + ")")) {
                System.out.println("fieldName: " + fieldName);
                return fieldName;
            }
        }
        return null;
    }

    /**
     * This method is responsible to validate if new value is valid integer
     *
     * @param newValue The new value that needs to be updated into DB.
     * @param jsonPath - The json path.
     */
    private void checkIfValidIntegerValue(Object newValue, String jsonPath) {
        if (newValue instanceof Integer) {
            if (StringUtils.contains(newValue.toString(), "-")) {
                throw new IllegalArgumentException("Patch cannot be applied if new value is negative integer for field - " + jsonPath);
            }
        } else {
            throw new IllegalArgumentException("Patch cannot be applied if new value is not valid positive integer for field - " + jsonPath);
        }
    }

    /**
     * This method is responsible to validate if new value is valid String
     *
     * @param newValue The new value that needs to be updated into DB.
     * @param jsonPath - The json path.
     */
    private void checkIfValidStringValue(Object newValue, String jsonPath) {
        if (newValue instanceof String) {
            if (StringUtils.isBlank(newValue.toString())) {
                throw new IllegalArgumentException("Patch cannot be applied if new value is blank for field - " + jsonPath);
            }
        } else {
            throw new IllegalArgumentException("Patch cannot be applied if new value is not valid String for field - " + jsonPath);
        }
    }

    /**
     * This method validates if the new value is a String, allowing for empty Strings.
     * @param newValue - The new value that needs to be updated into DB.
     * @param jsonPath - The JSON path.
     */
    private void checkIfString(Object newValue, String jsonPath) {
        if (!(newValue instanceof String)) {
            throw new IllegalArgumentException("Patch cannot be applied if new value is not a valid String for field - " + jsonPath);
        }
    }

    /**
     * This method validates if the component names are valid.
     * Component names must include only alphanumeric characters in lowercase and must begin with a letter.
     * Underscores and an empty string are also permitted.
     * @param components
     */
    private void checkIfValidComponent(String[] components) {
        for (String component : components) {
            if (!component.matches("^(?:[a-z][a-z0-9_]*)?$")) {
                throw new IllegalArgumentException("Patch cannot be applied if the component name is not valid - " + component);
            }
        }
    }

    /**
     * This method validates if the component list contains unique names.
     * @param components - A list of components.
     */
    private void checkIfUniqueComponent(String[] components) {
        for (int i = 0; i < components.length - 1; i++) {
            for (int j = i + 1; j < components.length; j++) {
                 if (components[i].equalsIgnoreCase(components[j])) {
                     throw new IllegalArgumentException("Patch cannot be applied if the component name is not unique - " + components[i]);
                 }
            }
        }
    }

    /**
     * This method validates values that depend on other values
     * @param newExemptComponents - The new list of Exempt components.
     * @param newAllTestsComponents - The new list of All-Tests components.
     * @param appProperty - Used to get the stored list, in case one was not sent.
     */
    private void checkDependencies(String[] newExemptComponents, String[] newAllTestsComponents, ApplicationProperty appProperty) {
        if (newExemptComponents != null && newAllTestsComponents != null) {
            checkIfMutuallyExclusiveComponents(newExemptComponents, newAllTestsComponents);
        } else if (newExemptComponents != null && newAllTestsComponents == null) {
            String oldAllTestsComponents = appProperty.getAllTestsComponents();
            if (oldAllTestsComponents != null) {
                checkIfMutuallyExclusiveComponents(newExemptComponents, oldAllTestsComponents.split(","));
            }
        } else if (newExemptComponents == null && newAllTestsComponents != null) {
            String oldExemptComponents = appProperty.getExemptComponents();
            if (oldExemptComponents != null) {
                checkIfMutuallyExclusiveComponents(oldExemptComponents.split(","), newAllTestsComponents);
            }
        }
    }

    /**
     * This method validates if the component lists are mutually exclusive.
     *
     * @param aComponents - A list of components.
     * @param bComponents - A list of components.
     */
    private void checkIfMutuallyExclusiveComponents(String[] aComponents, String[] bComponents) {
        for (int i = 0; i < aComponents.length; i++) {
            for (int j = 0; j < bComponents.length; j++) {
                 if (aComponents[i].equalsIgnoreCase(bComponents[j])) {
                     throw new IllegalArgumentException("Patch cannot be applied if the component lists are not mutually exclusive - " + aComponents[i]);
                 }
            }
        }
    }

    /**
     * @return Document containing updated application property fields, to be updated in the DB.
     */
    public Document getDocumentToUpdate() {
        return updateDocument;
    }

    /**
     * @param fieldName FieldName which needs to be checked for update.
     * @return True, indicating numberOfThreads to be updated in
     * ApplicationProperty.
     */
    public boolean isFieldUpdated(String fieldName) {
        if (fieldUpdatedMap != null && StringUtils.isNotBlank(fieldName)) {
            if (fieldUpdatedMap.containsKey(fieldName)) {
                return fieldUpdatedMap.get(fieldName);
            }
        }
        return false;
    }

    /**
     * This method is responsible to return the action from json patch
     * operation.
     *
     * @param jsonOp The Json operation.
     * @return The action associated to Json operation.
     */
    private String getJsonPatchAction(JsonPatch.Op jsonOp) {
        String action = "";
        switch (jsonOp) {
            case ADD:
                action = "Added ";
                break;
            case REPLACE:
                action = "Updated ";
                break;
        }
        return action;
    }

    /**
     * This method is responsible to create Application Property update activity.
     */
    private void createUpdateActivity(String userId, Activity.Action action, String eventName,
                                      String targetId, String fieldName, String currentVal, String prevVal, String description) {
        Activity updateActivity = new Activity.Builder(userId, eventName).action(action)
                .fieldName(fieldName).currentValue(currentVal).previousValue(prevVal)
                .description(description).targetId(targetId).build();

        activities.add(updateActivity);
    }

    /**
     * @return List of Application Property Update Activities created for Json
     * patch operations.
     */
    public List<Activity> getUpdateActivities() {
        return activities;
    }

}