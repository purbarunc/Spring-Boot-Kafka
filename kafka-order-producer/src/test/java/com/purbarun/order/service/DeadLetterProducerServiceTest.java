package com.purbarun.order.service;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-service", "order-service.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class DeadLetterProducerServiceTest {

    private static final String DLT_TOPIC = "order-service.DLT";

    @Autowired
    private DeadLetterProducerService deadLetterProducerService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Should route validation failure order to DLQ with VALIDATION_FAILURE type")
    void sendToDeadLetter_shouldPublishValidationFailureToDlqTopic() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "dlq-validation-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
            consumer.seekToEnd(consumer.assignment());
            consumer.assignment().forEach(consumer::position);

            deadLetterProducerService.sendToDeadLetter("ord-bad-001", "productName must not be blank; quantity must be greater than zero", "VALIDATION_FAILURE");

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC);
            assertThat(record.key()).isEqualTo("ord-bad-001");
            assertThat(record.value()).contains("\"failureType\":\"VALIDATION_FAILURE\"");
            assertThat(record.value()).contains("productName must not be blank");
            assertThat(record.topic()).isEqualTo(DLT_TOPIC);
        }
    }

    @Test
    @DisplayName("Should route undeliverable order to DLQ with PRODUCER_ERROR type")
    void sendToDeadLetter_shouldPublishProducerErrorToDlqTopic() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "dlq-producer-error-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
            consumer.seekToEnd(consumer.assignment());
            consumer.assignment().forEach(consumer::position);

            deadLetterProducerService.sendToDeadLetter("ord-bad-002", "Broker not available", "PRODUCER_ERROR");

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC);
            assertThat(record.key()).isEqualTo("ord-bad-002");
            assertThat(record.value()).contains("\"failureType\":\"PRODUCER_ERROR\"");
            assertThat(record.value()).contains("Broker not available");
        }
    }

    @Test
    @DisplayName("Should still publish to DLQ when orderId is null")
    void sendToDeadLetter_withNullOrderId_shouldStillPublishToDlqTopic() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "dlq-null-id-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
            consumer.seekToEnd(consumer.assignment());
            consumer.assignment().forEach(consumer::position);

            deadLetterProducerService.sendToDeadLetter(null, "price must be greater than zero", "VALIDATION_FAILURE");

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC);
            assertThat(record.value()).contains("price must be greater than zero");
            assertThat(record.value()).contains("\"failureType\":\"VALIDATION_FAILURE\"");
        }
    }
}
