package com.purbarun.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaOrderConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaOrderConsumerApplication.class, args);
	}

}
