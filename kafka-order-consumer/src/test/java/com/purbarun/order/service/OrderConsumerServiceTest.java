package com.purbarun.order.service;

import com.purbarun.order.model.Order;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
		"order-service" }, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = { "kafka.replay.mode=none", "spring.kafka.consumer.auto-offset-reset=latest" })
@DisplayName("OrderConsumerService — Kafka listener and replay behaviour")
class OrderConsumerServiceTest {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@MockitoSpyBean
	private OrderConsumerService orderConsumerService;

	@Value("${spring.kafka.topic.order}")
	private String topic;

	private KafkaTemplate<String, Order> testKafkaTemplate;

	@BeforeEach
	void setUp() {
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
		producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
		testKafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
	}

	@AfterEach
	void tearDown() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "none");
	}

	// -----------------------------------------------------------------------
	// Message consumption tests
	// -----------------------------------------------------------------------

	@Test
	@DisplayName("Given a message on the topic, listener deserializes all Order fields correctly")
	void consumeOrder_shouldReceiveMessageAndDeserializeAllFields() {
		Order order = new Order("ord-001", "Laptop", 2, new BigDecimal("1299.99"), "PENDING", LocalDateTime.now());

		testKafkaTemplate.send(topic, order.orderId(), order);

		ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
		verify(orderConsumerService, timeout(5000).times(1)).consumeOrder(captor.capture(), anyInt(), anyLong());

		Order received = captor.getValue();
		assertThat(received.orderId()).isEqualTo("ord-001");
		assertThat(received.productName()).isEqualTo("Laptop");
		assertThat(received.quantity()).isEqualTo(2);
		assertThat(received.price()).isEqualByComparingTo(new BigDecimal("1299.99"));
		assertThat(received.status()).isEqualTo("PENDING");
	}

	@Test
	@DisplayName("Given multiple messages on the topic, listener receives them all in order")
	void consumeOrder_shouldReceiveMultipleMessagesInOrder() {
		Order first = new Order("ord-002", "Keyboard", 1, new BigDecimal("99.99"), "NEW", LocalDateTime.now());
		Order second = new Order("ord-003", "Monitor", 1, new BigDecimal("499.99"), "NEW", LocalDateTime.now());

		testKafkaTemplate.send(topic, first.orderId(), first);
		testKafkaTemplate.send(topic, second.orderId(), second);

		ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
		verify(orderConsumerService, timeout(5000).times(2)).consumeOrder(captor.capture(), anyInt(), anyLong());

		List<Order> received = captor.getAllValues();
		assertThat(received).extracting(Order::orderId).containsExactly("ord-002", "ord-003");
	}

	// -----------------------------------------------------------------------
	// Replay mode onPartitionsAssigned seek tests
	// -----------------------------------------------------------------------

	@Test
	@DisplayName("Replay mode 'beginning' seeks every assigned partition to offset 0")
	void onPartitionsAssigned_withBeginningMode_shouldSeekAllPartitionsToBeginning() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "beginning");
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
		Map<TopicPartition, Long> assignments = Map.of(new TopicPartition(topic, 0), 0L, new TopicPartition(topic, 1),
				0L);

		orderConsumerService.onPartitionsAssigned(assignments, callback);

		verify(callback).seekToBeginning(topic, 0);
		verify(callback).seekToBeginning(topic, 1);
		verifyNoMoreInteractions(callback);
	}

	@Test
	@DisplayName("Replay mode 'timestamp' seeks every assigned partition to the given epoch millis")
	void onPartitionsAssigned_withTimestampMode_shouldSeekAllPartitionsToTimestamp() {
		long epochMillis = 1_713_456_789_000L;
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "timestamp");
		ReflectionTestUtils.setField(orderConsumerService, "replayTimestamp", epochMillis);
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
		Map<TopicPartition, Long> assignments = Map.of(new TopicPartition(topic, 0), 0L, new TopicPartition(topic, 1),
				0L);

		orderConsumerService.onPartitionsAssigned(assignments, callback);

		verify(callback).seekToTimestamp(topic, 0, epochMillis);
		verify(callback).seekToTimestamp(topic, 1, epochMillis);
		verifyNoMoreInteractions(callback);
	}

	@Test
	@DisplayName("Replay mode 'offset' seeks the specified partition to the exact offset")
	void onPartitionsAssigned_withOffsetMode_shouldSeekToSpecificPartitionAndOffset() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "offset");
		ReflectionTestUtils.setField(orderConsumerService, "replayPartition", 0);
		ReflectionTestUtils.setField(orderConsumerService, "replayOffset", 5L);
		ReflectionTestUtils.setField(orderConsumerService, "topic", topic);
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);

		orderConsumerService.onPartitionsAssigned(Map.of(new TopicPartition(topic, 0), 0L), callback);

		verify(callback).seek(topic, 0, 5L);
		verifyNoMoreInteractions(callback);
	}

	@Test
	@DisplayName("Replay mode 'none' (default) no seek invoked on partition assignment")
	void onPartitionsAssigned_withNoneMode_shouldNotInvokeAnySeek() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "none");
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);

		orderConsumerService.onPartitionsAssigned(Map.of(new TopicPartition(topic, 0), 0L), callback);

		verifyNoInteractions(callback);
	}

	@Test
	@DisplayName("Unknown replay mode falls through to default, no seek invoked")
	void onPartitionsAssigned_withUnknownMode_shouldNotInvokeAnySeek() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "invalid-mode");
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);

		orderConsumerService.onPartitionsAssigned(Map.of(new TopicPartition(topic, 0), 0L), callback);

		verifyNoInteractions(callback);
	}

	// -----------------------------------------------------------------------
	// Partition lifecycle tests
	// -----------------------------------------------------------------------

	@Test
	@DisplayName("Partition revoke — completes without throwing any exception")
	void onPartitionsRevoked_shouldNotThrow() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "none");
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
		TopicPartition tp = new TopicPartition(topic, 0);

		orderConsumerService.onPartitionsAssigned(Map.of(tp, 0L), callback);

		assertThatNoException().isThrownBy(() -> orderConsumerService.onPartitionsRevoked(List.of(tp)));
	}

	@Test
	@DisplayName("Partition revoke and removes callback so re-assignment uses only the new callback")
	void onPartitionsRevoked_shouldClearCallbacksForRevokedPartitions() {
		ReflectionTestUtils.setField(orderConsumerService, "replayMode", "beginning");
		ConsumerSeekAware.ConsumerSeekCallback firstCallback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
		ConsumerSeekAware.ConsumerSeekCallback secondCallback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);
		TopicPartition tp = new TopicPartition(topic, 0);

		orderConsumerService.onPartitionsAssigned(Map.of(tp, 0L), firstCallback);
		orderConsumerService.onPartitionsRevoked(List.of(tp));

		// Re-assign with a fresh callback — first callback must not be called again
		orderConsumerService.onPartitionsAssigned(Map.of(tp, 0L), secondCallback);

		verify(firstCallback, times(1)).seekToBeginning(topic, 0); // only from first assign
		verify(secondCallback, times(1)).seekToBeginning(topic, 0); // from re-assign
	}

	@Test
	@DisplayName("registerSeekCallback and no-op implementation does not interact with the callback")
	void registerSeekCallback_shouldBeNoOp() {
		ConsumerSeekAware.ConsumerSeekCallback callback = mock(ConsumerSeekAware.ConsumerSeekCallback.class);

		assertThatNoException().isThrownBy(() -> orderConsumerService.registerSeekCallback(callback));
		verifyNoInteractions(callback);
	}
}
