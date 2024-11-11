package com.paypal.sre.cfbt.data.execapi;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The system status request JSON.
 */
public class SystemStatusRequest {

    /**
     * Earliest date and time considered recent.
     */
    @JsonProperty("dateFrom")
    private String dateFrom;

    public Boolean getSynthetic() {
        return isSynthetic;
    }

    public void setSynthetic(Boolean synthetic) {
        isSynthetic = synthetic;
    }

    @JsonProperty("isSynthetic")
    private Boolean isSynthetic;

    /**
     * Get the date from.
     */
    @JsonProperty("dateFrom")
    public String getDateFrom() {
        return dateFrom;
    }

    /**
     * Set the date from.
     */
    @JsonProperty("dateFrom")
    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

}
