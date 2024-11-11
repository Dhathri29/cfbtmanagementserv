package com.paypal.sre.cfbt.management.kafka;

import java.util.Map;

import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.kernel.cal.util.StackTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypal.sre.cfbt.data.executor.TestExecutionSetRequest;
import com.paypal.sre.cfbt.shared.CFBTLogger;

/**
 * The Kafka serializer for {@link TestExecutionSetRequest}
 */
public class TestExecutionSetRequestSerializer implements Serializer<TestExecutionSetRequest> {

    private static final Logger logger = LoggerFactory.getLogger(TestExecutionSetRequestSerializer.class);

    @Override
    public void configure(Map configs, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, TestExecutionSetRequest data) {
        byte[] value = null;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            value = objectMapper.writeValueAsString(data.getRequest()).getBytes("UTF-8");
        } catch (Exception e) {
            CFBTLogger.logError(logger, TestExecutionSetRequestSerializer.class.getCanonicalName(),
                    "An error occured while trying to serialize TestExecutionSetRequest. Root cause : "
                            + StackTrace.getStackTrace(e));
        }
        return value;
    }

    @Override
    public void close() {
    }

}
