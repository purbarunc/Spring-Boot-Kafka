package com.purbarun.order.config;

import com.purbarun.order.model.DeadLetterOrder;
import com.purbarun.order.model.Order;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@NullMarked
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> baseProducerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return config;
    }

    @Bean
    ProducerFactory<String, Order> producerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerConfig());
    }

    @Bean
    KafkaTemplate<String, Order> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    ProducerFactory<String, DeadLetterOrder> dlqProducerFactory() {
        return new DefaultKafkaProducerFactory<>(baseProducerConfig());
    }

    @Bean
    KafkaTemplate<String, DeadLetterOrder> deadLetterKafkaTemplate() {
        return new KafkaTemplate<>(dlqProducerFactory());
    }
}
