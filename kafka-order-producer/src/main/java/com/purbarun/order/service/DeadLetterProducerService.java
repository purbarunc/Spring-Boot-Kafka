package com.purbarun.order.service;

import com.purbarun.order.model.DeadLetterOrder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/// # DeadLetterProducerService
///
/// Spring service responsible for publishing failed [Order] messages to the
/// Kafka dead-letter topic (`order-service.DLT`) as [DeadLetterOrder] payloads.
///
/// ## When is it invoked?
///
/// | Caller | Failure type |
/// |---|---|
/// | [OrderController] | `VALIDATION_FAILURE` -- order failed field validation |
/// | [OrderProducerService] | `PRODUCER_ERROR` -- Kafka send to main topic failed |
///
/// ## Configuration
///
/// | Property | Description |
/// |---|---|
/// | `spring.kafka.topic.order.dlt` | Target dead-letter Kafka topic |
@NullMarked
@Service
public class DeadLetterProducerService {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterProducerService.class);

    @Value("${spring.kafka.topic.order.dlt}")
    private String dlqTopic;

    private final KafkaTemplate<String, DeadLetterOrder> deadLetterKafkaTemplate;

    /// Creates a `DeadLetterProducerService` with the dedicated DLQ Kafka template.
    ///
    /// @param deadLetterKafkaTemplate template bound to the `order-service.DLT` producer factory
    public DeadLetterProducerService(KafkaTemplate<String, DeadLetterOrder> deadLetterKafkaTemplate) {
        this.deadLetterKafkaTemplate = deadLetterKafkaTemplate;
    }

    /// Publishes a [DeadLetterOrder] to the dead-letter topic asynchronously.
    ///
    /// Wraps the provided failure context into a [DeadLetterOrder] record with
    /// the current timestamp and sends it to `order-service.DLT` using `orderId`
    /// as the Kafka message key.
    ///
    /// The send result is handled via a `CompletableFuture` callback:
    ///
    /// - **Success** -- logs a `WARN` entry with orderId, failure type, and reason.
    /// - **Failure** -- logs an `ERROR` entry; the dead-letter message itself is lost.
    ///
    /// @param orderId       the original order identifier; may be `null` if unavailable
    /// @param failureReason human-readable description of why the order failed
    /// @param failureType   categorised failure label, e.g. `VALIDATION_FAILURE` or `PRODUCER_ERROR`
    public void sendToDeadLetter(@Nullable String orderId, String failureReason, String failureType) {
        DeadLetterOrder deadLetterOrder = new DeadLetterOrder(orderId, failureReason, failureType, LocalDateTime.now());
        deadLetterKafkaTemplate.send(dlqTopic, orderId, deadLetterOrder)
                .whenComplete((_, ex) -> {
                    if (ex == null) {
                        logger.warn("Order routed to DLQ: orderId={} | type={} | reason={}",
                                orderId, failureType, failureReason);
                    } else {
                        logger.error("Failed to route order to DLQ: orderId={} | error={}",
                                orderId, ex.getMessage());
                    }
                });
    }
}
