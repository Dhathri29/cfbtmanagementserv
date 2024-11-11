/*
 * (C) 2019 PayPal, Internal software, do not distribute.
 */
package com.paypal.sre.cfbt.lock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Data to be stored in the db.
 */
public class LockData {
    public enum LockStatus {
        LOCKED("Locked"),
        UNLOCKED("Unlocked");

        LockStatus(String val) {
            this.val = val;
        }

        private static Map<String, LockStatus> constants = new HashMap<String, LockStatus>();
        private String val;

        static {
            for (LockStatus c: LockStatus.values()) {
                constants.put(c.val, c);
            }
        }

        @JsonValue
        @Override
        public String toString() {
            return this.val;
        }

        @JsonCreator
        public static LockStatus fromValue(String value) {
            LockStatus constant = constants.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }
    }

    @JsonProperty("id") @NotNull @Valid private String id;
    @JsonProperty("lockStatus") private String lockStatus;
    @JsonProperty("lockKey") private String lockKey;
    @JsonProperty("lockName") private String lockName;
    @JsonProperty("timeLocked") private String timeLocked;
    @JsonProperty("timeUpdated") private String timeUpdated;

    @JsonProperty("id") public String getId() { return id; }
    @JsonProperty("lockStatus") public String getLockStatus() { return lockStatus; }
    @JsonProperty("lockKey") public String getLockKey() { return lockKey; } 
    @JsonProperty("lockName") public String getLockName() { return lockName; }
    @JsonProperty("timeLocked") public String getTimeLocked() { return timeLocked; }
    @JsonProperty("timeUpdated") public String getTimeUpdated() { return timeUpdated; }

    @JsonProperty("id") public void setId(String id) { this.id = id; }
    @JsonProperty("lockStatus") public void setLockStatus(String lockStatus) { this.lockStatus = lockStatus; }
    @JsonProperty("lockKey") public void setLockKey(String lockKey) { this.lockKey = lockKey; }
    @JsonProperty("lockName") public void setLockName(String lockName) { this.lockName = lockName; }
    @JsonProperty("timeLocked") public void setTimeLocked(String timeLocked) { this.timeLocked = timeLocked; }
    @JsonProperty("timeUpdated") public void setTimeUpdated(String timeUpdated) { this.timeUpdated = timeUpdated; }
}
