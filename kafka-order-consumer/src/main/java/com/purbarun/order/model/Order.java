package com.purbarun.order.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NullMarked
public record Order(
        @Nullable String orderId,
        String productName,
        int quantity,
        BigDecimal price,
        String status,
        @Nullable LocalDateTime createdAt
) {
}
