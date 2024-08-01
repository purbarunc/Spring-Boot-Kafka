package org.purbarun.kafka.service;

import org.purbarun.kafka.model.OrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {
	@KafkaListener(groupId = "NEW_ORDER_group", topics = "NEW_ORDER",containerFactory = "orderListener")
	public void consume(OrderMessage orderMessage,Acknowledgment acknowledgment) {
		System.out.println(orderMessage);
		acknowledgment.acknowledge();
	}
}
