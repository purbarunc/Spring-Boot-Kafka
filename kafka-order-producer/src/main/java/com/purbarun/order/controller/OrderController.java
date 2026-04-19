package com.purbarun.order.controller;

import com.purbarun.order.exception.OrderValidationException;
import com.purbarun.order.model.Order;
import com.purbarun.order.service.DeadLetterProducerService;
import com.purbarun.order.service.OrderProducerService;
import com.purbarun.order.validator.OrderValidator;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@NullMarked
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderProducerService orderProducerService;
    private final DeadLetterProducerService deadLetterProducerService;
    private final OrderValidator orderValidator;

    public OrderController(OrderProducerService orderProducerService,
            DeadLetterProducerService deadLetterProducerService,
            OrderValidator orderValidator) {
        this.orderProducerService = orderProducerService;
        this.deadLetterProducerService = deadLetterProducerService;
        this.orderValidator = orderValidator;
    }

    @PostMapping
    public ResponseEntity<String> placeOrder(@RequestBody Order order) {
        String orderId = (order.orderId() == null || order.orderId().isBlank())
                ? UUID.randomUUID().toString() : order.orderId();
        LocalDateTime createdAt = order.createdAt() != null ? order.createdAt() : LocalDateTime.now();
        Order enriched = new Order(orderId, order.productName(), order.quantity(), order.price(), order.status(), createdAt);

        try {
            orderValidator.validate(enriched);
        } catch (OrderValidationException e) {
            deadLetterProducerService.sendToDeadLetter(orderId, e.getMessage(), "VALIDATION_FAILURE");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body("Order rejected: orderId=" + orderId + " | reason=" + e.getMessage());
        }

        orderProducerService.sendOrder(enriched);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Order accepted | orderId=" + orderId);
    }
}
