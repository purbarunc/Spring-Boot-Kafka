# kafka-order-producer

A Spring Boot 4 microservice that publishes order messages as JSON payloads to a Kafka topic, with a built-in Dead Letter Queue (DLQ) system for malformed or undeliverable messages.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4 |
| Messaging | Spring for Apache Kafka 4 |
| Serialization | `JacksonJsonSerializer` (Jackson 3 / `tools.jackson`) |
| Language | Java 25 |
| Build | Gradle |
| Null Safety | JSpecify 1.0.0 (`@NullMarked` / `@Nullable`) |

---

## Package Structure

```
com.purbarun.order
├── KafkaOrderProducerApplication.java
├── config/
│   └── KafkaProducerConfig.java          # Kafka producer beans (main + DLQ)
├── controller/
│   └── OrderController.java              # REST endpoint
├── exception/
│   └── OrderValidationException.java     # Unchecked validation exception
├── model/
│   ├── Order.java                        # Order record (input payload)
│   └── DeadLetterOrder.java              # DLQ record (failure envelope)
├── service/
│   ├── OrderProducerService.java         # Publishes to order-service topic
│   └── DeadLetterProducerService.java    # Publishes to order-service.DLT topic
└── validator/
    └── OrderValidator.java               # Validates incoming Order fields
```

---

## Order Message Flow

```
POST /api/orders  (JSON body)
        │
        ▼
┌─────────────────┐
│ OrderController │
│                 │
│  1. Enrich      │  ── auto-generates orderId (UUID) if absent
│     order       │  ── auto-sets createdAt to now() if absent
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  OrderValidator │
└────────┬────────┘
         │
    ┌────┴─────────────────────────────────────────┐
    │                                              │
  VALID                                        INVALID
    │                                              │
    ▼                                              ▼
┌────────────────────┐               ┌──────────────────────────┐
│ OrderProducerService│               │ DeadLetterProducerService │
│                    │               │                          │
│  kafkaTemplate     │               │  failureType =           │
│  .send(topic, ...) │               │  "VALIDATION_FAILURE"    │
└────────┬───────────┘               └──────────┬───────────────┘
         │                                       │
    ┌────┴──────────┐                            │
    │               │                            │
 SUCCESS         FAILURE                         │
    │               │                            │
    ▼               ▼                            ▼
202 Accepted   DeadLetterProducerService    order-service.DLT
               │                           (Kafka topic)
               │  failureType =
               │  "PRODUCER_ERROR"
               │
               ▼
          order-service.DLT
          (Kafka topic)
```

---

## Kafka Topics

| Topic | Purpose |
|---|---|
| `order-service` | Main topic — receives all valid, deliverable orders |
| `order-service.DLT` | Dead Letter Topic — receives all failed orders with failure context |

---

## Dead Letter Queue (DLQ) System

### Purpose

The DLQ is an operational safety net that ensures **no message is ever silently lost**.

### Value it adds

**1. Zero message loss**
Bad or undeliverable messages are preserved in `order-service.DLT` with full context. Nothing disappears silently.

**2. Root cause diagnosis**
Each `DeadLetterOrder` carries structured failure metadata:

```json
{
  "orderId": "abc-123",
  "failureReason": "productName must not be blank; price must be greater than zero",
  "failureType": "VALIDATION_FAILURE",
  "failedAt": "2026-04-18T20:00:00"
}
```

**3. Replayability**
A consumer can read `order-service.DLT`, fix the data, and republish corrected messages to `order-service` — no code changes or restarts required.

**4. Separates failure modes**

| `failureType` | Meaning | Remediation |
|---|---|---|
| `VALIDATION_FAILURE` | Caller sent a malformed payload | Fix client-side request |
| `PRODUCER_ERROR` | Kafka broker unavailable / send timed out | Infrastructure / ops issue |

**5. Prevents silent data loss**
Without a DLQ, a `PRODUCER_ERROR` (broker down, network timeout) drops orders permanently. With the DLQ, those orders are safely parked and can be replayed once the broker recovers.

### Validation Rules

An order is routed to the DLQ with `VALIDATION_FAILURE` if any of the following are violated:

| Field | Rule |
|---|---|
| `productName` | Must not be null or blank |
| `quantity` | Must be greater than zero |
| `price` | Must not be null and must be greater than zero |
| `status` | Must not be null or blank |

---

## REST API

### Publish an Order

```
POST /api/orders
Content-Type: application/json
```

**Request body**

```json
{
  "productName": "Laptop",
  "quantity": 2,
  "price": 1299.99,
  "status": "PENDING"
}
```

> `orderId` and `createdAt` are optional — they are auto-generated if absent.

**Responses**

| HTTP Status | Meaning |
|---|---|
| `202 Accepted` | Order successfully published to `order-service` |
| `422 Unprocessable Content` | Validation failed — order routed to `order-service.DLT` |

**Sample — valid order**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"Laptop","quantity":2,"price":1299.99,"status":"PENDING"}'
```

**Sample — invalid order (triggers DLQ)**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productName":"","quantity":-1,"price":0,"status":""}'
```

---

## Configuration

```properties
# application.properties

spring.application.name=kafka-order-producer

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer
spring.kafka.topic.order=order-service
spring.kafka.topic.order.dlt=order-service.DLT
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3

# Logging
logging.level.org.apache.kafka.common.config.AbstractConfig=WARN
```

---

## Prerequisites

- Java 25
- Apache Kafka broker running on `localhost:9092`
- Topics `order-service` and `order-service.DLT` created on the broker

> If `auto.create.topics.enable=true` on the broker, both topics are created automatically on first use.
