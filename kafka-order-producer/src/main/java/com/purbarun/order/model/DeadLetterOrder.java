package com.purbarun.order.model;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

@NullMarked
public record DeadLetterOrder(
        @Nullable String orderId,
        String failureReason,
        String failureType,
        LocalDateTime failedAt
) {
}
