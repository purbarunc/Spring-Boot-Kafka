package org.purbarun.kafka.service;

import org.purbarun.kafka.model.OrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KafkaConsumer {
	@KafkaListener(groupId = "NEW_ORDER_group", topics = "NEW_ORDER")
	public void consume(OrderMessage orderMessage,Acknowledgment acknowledgment) {
		log.info("Order Received from Kafka => {}", orderMessage);
		acknowledgment.acknowledge();
	}
}
