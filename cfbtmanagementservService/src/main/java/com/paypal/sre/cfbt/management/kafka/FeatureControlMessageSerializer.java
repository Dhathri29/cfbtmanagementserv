package com.paypal.sre.cfbt.management.kafka;

import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.kernel.cal.util.StackTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.sre.cfbt.data.features.FeatureControlMessage;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * The Kafka serializer for {@link FeatureControlMessage}
 */
public class FeatureControlMessageSerializer implements Serializer<FeatureControlMessage> {

    private static final Logger logger = LoggerFactory.getLogger(FeatureControlMessageSerializer.class);

    @Override
    public void configure(Map configs, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, FeatureControlMessage data) {
        byte[] value = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            value = objectMapper.writeValueAsString(data).getBytes("UTF-8");
        } catch (Exception e) {
            CFBTLogger.logError(logger, FeatureControlMessageSerializer.class.getCanonicalName(),
                    "An error occured while trying to serialize FeatureControlMessage. Root cause : "
                            + StackTrace.getStackTrace(e));
        }
        return value;
    }

    @Override
    public void close() {
    }

}
