package com.purbarun.order.service;

import com.purbarun.order.model.Order;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

/// Spring service that consumes `Order` messages from the configured Kafka topic.
///
/// Implements [ConsumerSeekAware] to support **message replay** before the first poll.
/// Replay behaviour is driven entirely by JVM/application properties and no web layer required:
///
/// | Property | Default | Description |
/// |---|---|---|
/// | `kafka.replay.mode` | `none` | One of `none`, `beginning`, `timestamp`, `offset` |
/// | `kafka.replay.timestamp` | `0` | Epoch millis used when mode is `timestamp` |
/// | `kafka.replay.partition` | `-1` | Target partition used when mode is `offset` |
/// | `kafka.replay.offset` | `-1` | Target offset used when mode is `offset` |
///
/// **Example  replay from the beginning via Gradle:**
/// ```bash
/// ./gradlew bootRun --args='--kafka.replay.mode=beginning'
/// ```
@NullMarked
@Service
public class OrderConsumerService implements ConsumerSeekAware {

	private static final Logger logger = LoggerFactory.getLogger(OrderConsumerService.class);

	/// Replay mode read from `kafka.replay.mode` (default `none`).
	@Value("${kafka.replay.mode:none}")
	private String replayMode;

	/// Epoch-millisecond timestamp used when `replayMode` is `timestamp`.
	@Value("${kafka.replay.timestamp:0}")
	private long replayTimestamp;

	/// Partition index used when `replayMode` is `offset`.
	@Value("${kafka.replay.partition:-1}")
	private int replayPartition;

	/// Absolute offset used when `replayMode` is `offset`.
	@Value("${kafka.replay.offset:-1}")
	private long replayOffset;

	/// Kafka topic name injected from `spring.kafka.topic.order`.
	@Value("${spring.kafka.topic.order}")
	private String topic;

	/// No-op  per-partition callbacks are handled in [#onPartitionsAssigned].
	@Override
	public void registerSeekCallback(ConsumerSeekCallback callback) {
	}

	/// Called by the Kafka listener container each time partitions are assigned to this consumer,
	/// **before** the first `poll()`. Seeks all assigned partitions according to `replayMode`:
	///
	/// - `beginning`  seeks every partition to offset 0
	/// - `timestamp`  seeks every partition to the first offset at or after [#replayTimestamp]
	/// - `offset`  seeks [#replayPartition] to [#replayOffset] exactly
	/// - anything else  no seek; consumption resumes from last committed offset
	///
	/// @param assignments map of assigned [TopicPartition]s to their current committed offsets
	/// @param callback    seek callback provided by the listener container
	@Override
	public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
		logger.info("Partitions assigned: {} | replayMode={}", assignments.keySet(), replayMode);
		switch (replayMode) {
		case "beginning" -> {
			logger.info("Replay mode: seeking all partitions to beginning");
			assignments.keySet().forEach(tp -> callback.seekToBeginning(tp.topic(), tp.partition()));
		}
		case "timestamp" -> {
			logger.info("Replay mode: seeking all partitions to timestamp={}", replayTimestamp);
			assignments.keySet().forEach(tp -> callback.seekToTimestamp(tp.topic(), tp.partition(), replayTimestamp));
		}
		case "offset" -> {
			logger.info("Replay mode: seeking partition={} to offset={}", replayPartition, replayOffset);
			callback.seek(topic, replayPartition, replayOffset);
		}
		default -> logger.info("Normal mode: consuming from current committed offsets");
		}
	}

	/// Receives a single `Order` message from the Kafka topic and logs its details.
	///
	/// @param order     the deserialized [Order] payload
	/// @param partition Kafka partition the message was read from
	/// @param offset    offset of the message within the partition
	@KafkaListener(topics = "${spring.kafka.topic.order}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
	public void consumeOrder(@Payload Order order, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
			@Header(KafkaHeaders.OFFSET) long offset) {
		logger.info(
				"Order received: orderId={} | product={} | quantity={} | price={} | status={} | partition={} | offset={}",
				order.orderId(), order.productName(), order.quantity(), order.price(), order.status(), partition,
				offset);
	}
}
