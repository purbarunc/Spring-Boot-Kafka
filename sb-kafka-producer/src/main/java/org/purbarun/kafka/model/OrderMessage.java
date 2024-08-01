package org.purbarun.kafka.model;

public record OrderMessage (OrderRequest orderRequest,String messageId) {
}
