package com.purbarun.order.validator;

import com.purbarun.order.exception.OrderValidationException;
import com.purbarun.order.model.Order;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@NullMarked
@Component
public class OrderValidator {

    public void validate(Order order) {
        List<String> errors = new ArrayList<>();

        if (order.productName() == null || order.productName().isBlank()) {
            errors.add("productName must not be blank");
        }
        if (order.quantity() <= 0) {
            errors.add("quantity must be greater than zero");
        }
        if (order.price() == null || order.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("price must be greater than zero");
        }
        if (order.status() == null || order.status().isBlank()) {
            errors.add("status must not be blank");
        }

        if (!errors.isEmpty()) {
            throw new OrderValidationException(String.join("; ", errors));
        }
    }
}
