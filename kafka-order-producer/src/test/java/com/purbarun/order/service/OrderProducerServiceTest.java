package com.purbarun.order.service;

import com.purbarun.order.model.Order;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-service", "order-service.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class OrderProducerServiceTest {

    private static final String ORDER_TOPIC = "order-service";

    @Autowired
    private OrderProducerService orderProducerService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Should publish order as JSON to the order-service Kafka topic")
    void sendOrder_shouldPublishMessageToOrderTopic() {
        Order order = new Order("ord-001", "Laptop", 2, new BigDecimal("1299.99"), "PENDING", LocalDateTime.now());

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "order-test-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, ORDER_TOPIC);
            consumer.seekToEnd(consumer.assignment());
            consumer.assignment().forEach(consumer::position);

            orderProducerService.sendOrder(order);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, ORDER_TOPIC);
            assertThat(record.key()).isEqualTo("ord-001");
            assertThat(record.value()).contains("\"productName\":\"Laptop\"");
            assertThat(record.value()).contains("\"quantity\":2");
            assertThat(record.value()).contains("\"status\":\"PENDING\"");
        }
    }

    @Test
    @DisplayName("Should use orderId as the Kafka message key")
    void sendOrder_shouldUseOrderIdAsMessageKey() {
        Order order = new Order("ord-002", "Keyboard", 1, new BigDecimal("99.99"), "NEW", LocalDateTime.now());

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(embeddedKafkaBroker, "order-key-test-group", true);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, ORDER_TOPIC);
            consumer.seekToEnd(consumer.assignment());
            consumer.assignment().forEach(consumer::position);

            orderProducerService.sendOrder(order);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, ORDER_TOPIC);
            assertThat(record.key()).isEqualTo("ord-002");
            assertThat(record.topic()).isEqualTo(ORDER_TOPIC);
        }
    }
}
