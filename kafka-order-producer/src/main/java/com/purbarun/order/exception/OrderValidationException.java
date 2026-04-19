package com.purbarun.order.exception;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class OrderValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

	public OrderValidationException(String message) {
        super(message);
    }
}
