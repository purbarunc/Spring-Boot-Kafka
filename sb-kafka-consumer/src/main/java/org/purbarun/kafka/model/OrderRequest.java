package org.purbarun.kafka.model;

public record OrderRequest(String item,int quantity,int price) {
}
