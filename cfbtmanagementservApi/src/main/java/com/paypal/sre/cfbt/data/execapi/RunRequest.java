
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paypal.platform.error.api.CommonError;
import com.paypal.sre.cfbt.shared.CFBTExceptionUtil;


/**
 * The request json to execute a test suite.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "testIds",
    "executionRequestId",
    "dcg",
    "trReleaseId",
    "requestType",
    "priority",
    "systemUnderTest",
    "runNow",
    "syntheticLocation"
})
public class RunRequest {

    @JsonProperty("syntheticLocation")
    private String syntheticLocation;

    /**
     * The tests to execute.
     */
    @JsonProperty("testIds")
    private List<String> testIds = new ArrayList<String>();
    /**
     * Used for re-run, pick a previous execution request and re-execute it.
     * 
     */
    @JsonProperty("executionRequestId")
    private String executionRequestId;
    /**
     * The data center group name.
     * 
     */
    @JsonProperty("dcg")
    @Deprecated
    private String dcg;

    /**
     * Turbo roller release identification number.
     * 
     */
    @JsonProperty("trReleaseId")
    private String trReleaseId;
    /**
     * The type of request (release or basic).
     * 
     */
    @JsonProperty("requestType")
    private ExecutionRequest.Type requestType = ExecutionRequest.Type.ADHOC;
    
    @JsonProperty("priority")
    private int priority = 2; //Setting the deafult priority as low
    
    @JsonProperty("runNow")
    private boolean runNow = false;

    /**
     * System Under Test
     *
     */
    @JsonProperty("systemUnderTest")
    private SystemUnderTest systemUnderTest;

    /**
     * A check to determine if this is a valid RunRequest.
     * 
     * Either tests must be passed in or a request id to copy the tests from
     */
    public void validate() {
        if (!thisHasTests() && !thisIsRerun()) {
            CFBTExceptionUtil.throwBusinessException(CommonError.VALIDATION_ERROR,
                    "must have one argument - test-ids or execution-request-id", null);
        }
    }

    /**
     * A check to determine if request is a rerun
     */
    public boolean thisIsRerun() {
        return executionRequestId != null;
    }

    /**
     * A check to determine if request is has tests
     */
    public boolean thisHasTests() {
        return !testIds.isEmpty() && testIds.size() > 0;
    }

    /**
     * 
     */
    @JsonProperty("testIds")
    public List<String> getTestIds() {
        return testIds;
    }

    /**
     * 
     */
    @JsonProperty("testIds")
    public void setTestIds(List<String> testIds) {
        this.testIds = testIds;
    }

    /**
     * Used for re-run, pick a previous execution request and re-execute it.
     * 
     */
    @JsonProperty("executionRequestId")
    public String getExecutionRequestId() {
        return executionRequestId;
    }
    
    /**
     * Used for re-run, pick a previous execution request and re-execute it.
     * 
     */
    @JsonProperty("executionRequestId")
    public void setExecutionRequestId(String executionRequestId) {
        this.executionRequestId = executionRequestId;
    }

    /**
     * The data center group name.
     * 
     */
    @JsonProperty("dcg")
    @Deprecated
    public String getDcg() {
        return dcg;
    }

    /**
     * The data center group name.
     * 
     */
    @JsonProperty("dcg")
    @Deprecated
    public void setDcg(String dcg) {
        this.dcg = dcg;
    }

    /**
     * Turbo roller release identification number.
     * 
     */
    @JsonProperty("trReleaseId")
    public String getTrReleaseId() {
        return trReleaseId;
    }

    public void setSyntheticLocation(String syntheticLocation) {
        this.syntheticLocation = syntheticLocation;
    }

    public String getSyntheticLocation() {
        return this.syntheticLocation;
    }
    
    /**
     * Priority information.
     * 
     */
    @JsonProperty("priority")
    public int getPriority() {
        return priority;
    }

    /**
     * Turbo roller release identification number.
     * 
     */
    @JsonProperty("trReleaseId")
    public void setTrReleaseId(String trReleaseId) {
        this.trReleaseId = trReleaseId;
    }

    /**
     * The type of request (release or basic).
     * 
     */
    @JsonProperty("requestType")
    public ExecutionRequest.Type getRequestType() {
        return requestType;
    }

    /**
     * The type of request (release or basic).
     * 
     */
    @JsonProperty("requestType")
    public void setRequestType(ExecutionRequest.Type requestType) {
        this.requestType = requestType;
    }
    
    /**
     * Set the priority details
     * 
     */
    @JsonProperty("priority")
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
    * System Under Test
    */
   public SystemUnderTest getSystemUnderTest() {
       return this.systemUnderTest;
   }

   /**
    * System Under Test
    */
   public void setSystemUnderTest(final SystemUnderTest systemUnderTest) {
       this.systemUnderTest = systemUnderTest;
   }
   
   /**
     * Set the flag to indicate execute immediately
     * 
     */
    @JsonProperty("runNow")
    public void setRunNow(boolean runNow) {
        this.runNow = runNow;
    }
    
    /**
    * 
    * @return Flag value to indicate whether execution is immediate or not.
    */
   public boolean getRunNow() {
       return runNow;
   }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
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
