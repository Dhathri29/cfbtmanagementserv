package com.paypal.sre.cfbt.management.appproperty;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * This enum handles Wait Time types info.
 */
public enum WaitTimeType {
    NormalWaitTime("NormalWaitTime"),
    MediumWaitTime("MediumWaitTime"),
    LargeWaitTime("LargeWaitTime");

    private final String value;

    WaitTimeType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonCreator
    public static WaitTimeType fromValue(String value) {
        for (WaitTimeType thisValue : WaitTimeType.values()) {
            if (thisValue.value.equals(value)) {
                return thisValue;
            }
        }
        return null;
    }
}
