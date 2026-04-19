package com.purbarun.order.service;

import com.purbarun.order.model.Order;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/// # OrderProducerService
///
/// Spring service responsible for publishing validated [Order] messages to the
/// main Kafka topic (`order-service`) as JSON payloads.
///
/// ## Failure handling
///
/// If the Kafka send fails (e.g. broker unavailable, network timeout), the order
/// is automatically forwarded to the dead-letter topic (`order-service.DLT`) via
/// [DeadLetterProducerService] with failure type `PRODUCER_ERROR`.
///
/// ## Configuration
///
/// | Property | Description |
/// |---|---|
/// | `spring.kafka.topic.order` | Target Kafka topic for valid orders |
/// | `spring.kafka.bootstrap-servers` | Kafka broker address |
@NullMarked
@Service
public class OrderProducerService {

	private static final Logger logger = LoggerFactory.getLogger(OrderProducerService.class);

	@Value("${spring.kafka.topic.order}")
	private String topic;

	private final KafkaTemplate<String, Order> kafkaTemplate;
	private final DeadLetterProducerService deadLetterProducerService;

	/// Creates an `OrderProducerService` with the required Kafka dependencies.
	///
	/// @param kafkaTemplate            template for sending [Order] messages to Kafka
	/// @param deadLetterProducerService service for routing failed messages to the DLQ
	public OrderProducerService(KafkaTemplate<String, Order> kafkaTemplate,
			DeadLetterProducerService deadLetterProducerService) {
		this.kafkaTemplate = kafkaTemplate;
		this.deadLetterProducerService = deadLetterProducerService;
	}

	/// Sends an [Order] to the configured Kafka topic asynchronously.
	///
	/// The send result is handled via a `CompletableFuture` callback:
	///
	/// - **Success** -- logs partition and offset metadata at `INFO` level.
	/// - **Failure** -- logs the error at `ERROR` level and forwards the order
	///   to the dead-letter topic with failure type `PRODUCER_ERROR`.
	///
	/// @param order the validated order to publish; `orderId` is used as the Kafka message key
	public void sendOrder(Order order) {
		CompletableFuture<SendResult<String, Order>> future = kafkaTemplate.send(topic, order.orderId(), order);

		future.whenComplete((result, ex) -> {
			if (ex == null) {
				logger.info("Order sent successfully: orderId={} | partition={} | offset={}", order.orderId(),
						result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
			} else {
				logger.error("Failed to send order: orderId={} | error={}", order.orderId(), ex.getMessage());
				deadLetterProducerService.sendToDeadLetter(order.orderId(), ex.getMessage(), "PRODUCER_ERROR");
			}
		});
	}
}
