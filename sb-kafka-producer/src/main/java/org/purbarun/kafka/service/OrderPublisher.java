package org.purbarun.kafka.service;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.purbarun.kafka.model.OrderMessage;
import org.purbarun.kafka.model.OrderRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderPublisher {
	private KafkaTemplate<String, OrderMessage> kafkaTemplate;
	
	public OrderPublisher(KafkaTemplate<String, OrderMessage> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}


	public String pushOrderToQueue(OrderRequest orderRequest) throws InterruptedException, ExecutionException {
		String msgId = UUID.randomUUID().toString();
		OrderMessage orderMessage = new OrderMessage(orderRequest, msgId);
		kafkaTemplate.send("NEW_ORDER", orderMessage).get();
		return msgId;
	}

}
