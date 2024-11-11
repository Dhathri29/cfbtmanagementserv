package com.paypal.sre.cfbt.management.kafka;

import java.util.List;
import java.util.Properties;
import javax.inject.Inject;
import org.apache.commons.configuration.Configuration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import com.paypal.infra.messaging.kafka.client.resiliency.ConfigServiceHelper;
import com.paypal.infra.messaging.kafka.client.resiliency.KafkaConfigServiceInput;
import com.paypal.infra.messaging.kafka.client.resiliency.KafkaConfigServiceInput.ClientRole;
import com.paypal.infra.messaging.kafka.client.resiliency.KafkaConfigServiceInputRaptorBuilder;
import com.paypal.infra.messaging.kafka.recovery.model.ClientEnvironment;
import com.paypal.sre.cfbt.data.executor.TestExecutionContainer;
import com.paypal.sre.cfbt.data.executor.TestExecutionSetRequest;

/**
 * Test Execution Kafka Producer class
 */
@Component
public class TestExecutionProducer {

    private static Producer<String, TestExecutionSetRequest> producer;
    public static final String VALUE_SERIALIZER = "com.paypal.sre.cfbt.management.kafka.TestExecutionSetRequestSerializer";
    public static final String KEY_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String INTERCEPTOR = "com.paypal.kafka.clients.interceptors.DatalossMonitoringProducerInterceptor";
    public static final String METRICS_REPORTER = "com.paypal.kafka.reporters.KafkaClientMetricsReporter";
    public static final String CLIENT_ID = "test-execution-producer";
    private static String topic;

    @Inject
    private TestExecutionProducer(Configuration config) {

        Properties props = getProducerProperties();

        ClientEnvironment clientEnv = new ClientEnvironment();
        ClientRole clientRole = ClientRole.PRODUCER;
        String clientId = props.getProperty("client.id");
        topic = config.getString(Topic.Test_Execution_Events.getProperty(), Topic.Test_Execution_Events.getName());
        int connectionTimeout = config.getInt("cfbtmanagementserv.kafka.connectionTimeout", 3000);
        int requestTimeout = config.getInt("cfbtmanagementserv.kafka.requestTimeout", 60000);

        KafkaConfigServiceInput.ConnectionConfig connectionConfig = new KafkaConfigServiceInput.ConnectionConfig(
                connectionTimeout, requestTimeout);

        KafkaConfigServiceInput kafkaConfigServiceInput = new KafkaConfigServiceInputRaptorBuilder()
                .withClientId(clientId).withClientRole(clientRole).withClientEnv(clientEnv).withTopics(topic)
                .withConnectionConfig(connectionConfig).get();

        ConfigServiceHelper configServiceHelper = new ConfigServiceHelper();
        props = configServiceHelper.getKafkaConfigs(kafkaConfigServiceInput, props);

        producer = new KafkaProducer<>(props);
    }

    /**
     * @return the Kafka {@link Producer} for {@link TestExecutionSetRequest}
     */
    public synchronized static Producer<String, TestExecutionSetRequest> getProducer() {
        return producer;
    }

    public synchronized static void setTopic(String topic) {
        TestExecutionProducer.topic = topic;
    }

    /**
     *
     * @param producer Set the static value producer {@link Producer}
     */
    public synchronized static void setProducer(Producer<String, TestExecutionSetRequest> producer) {
        TestExecutionProducer.producer = producer;
    }

    /**
     * Method to create a kafka test execution record and publish it to the corresponding kafka topic.
     * 
     * @param testExecutionContainerList the list of {@link TestExecutionContainer}
     */
    public static void scheduleTasks(List<TestExecutionContainer> testExecutionContainerList) {
        TestExecutionSetRequest testExecutionSetReq = new TestExecutionSetRequest(testExecutionContainerList);
        ProducerRecord<String, TestExecutionSetRequest> record = new ProducerRecord<>(
                topic, testExecutionSetReq);
        getProducer().send(record);
    }

    /**
     * @param config the {@link Configuration} to be used for initialization
     * @return the Kafka Producer {@link Properties}
     */
    private Properties getProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KEY_SERIALIZER);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, INTERCEPTOR);
        props.put("kafka.monitoring.pool", "cfbtmanagementserv");
        props.put(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, METRICS_REPORTER);
        return props;
    }
}
