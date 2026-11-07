# kafka-order-consumer

A Spring Boot Kafka consumer that listens to the `order-service` topic produced by `kafka-order-producer`.

---

## Project Structure

```
src/main/java/com/purbarun/order/
├── KafkaOrderConsumerApplication.java   # Spring Boot entry point
├── config/
│   └── KafkaConsumerConfig.java         # ConsumerFactory + ListenerContainerFactory
├── model/
│   └── Order.java                       # Order record (mirrors producer model)
└── service/
    └── OrderConsumerService.java        # @KafkaListener + ConsumerSeekAware (replay)
```

---

## Configuration

`src/main/resources/application.properties`

| Property | Value | Description |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `spring.kafka.consumer.group-id` | `order-consumer-group` | Consumer group ID |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | Start from beginning if no committed offset |
| `spring.kafka.topic.order` | `order-service` | Topic name to consume from |

---

## How the App Keeps Running (Continuous Polling)

The app has **no web server**. It stays alive purely because of Kafka's listener threads:

1. `ConcurrentKafkaListenerContainerFactory` starts a background listener thread per partition when the app boots.
2. Each thread runs an internal `while (isRunning()) { consumer.poll(timeout); }` loop — blocking on `poll()` indefinitely.
3. These are **non-daemon threads**, so the JVM does not exit as long as they are alive.
4. On shutdown (`Ctrl+C` / SIGTERM), Spring's shutdown hook stops the listener containers, commits offsets, and closes the Kafka consumer cleanly.

---

## Why Messages Are Not Re-consumed on Restart

Kafka tracks **how far each consumer group has read** by storing committed offsets in its internal
`__consumer_offsets` topic on the broker — one offset per `(group-id, topic, partition)` combination.

This app uses group-id `order-consumer-group`. Here is what happens across restarts:

```
First run
─────────
  No committed offset exists for "order-consumer-group"
  → auto-offset-reset=earliest kicks in
  → consumer reads from offset 0 on every partition
  → after each poll batch, Spring Kafka commits the offsets back to Kafka

Second run (and every run after)
─────────────────────────────────
  Committed offset for "order-consumer-group" already exists (e.g. offset 42)
  → auto-offset-reset is IGNORED — it only applies when there is NO committed offset
  → consumer picks up from offset 43
  → no new messages → nothing consumed
```

> `auto-offset-reset=earliest` is a **bootstrap policy only**. Once a committed offset exists on the
> broker for the group, that setting has no effect regardless of what is in `application.properties`.

### Options to re-consume existing messages

| Approach | How | Side effect |
|---|---|---|
| **Replay mode (built-in)** | `--kafka.replay.mode=beginning` at startup | Only this instance re-reads; committed offset on broker is **not** changed |
| **Reset the group offset** | `kafka-consumer-groups.sh --reset-offsets --to-earliest --group order-consumer-group --topic order-service --execute` | Permanently changes the committed offset for the whole group |
| **Change the group-id** | Use a new value for `spring.kafka.consumer.group-id` | Acts like a first-time consumer; old group offset is left intact |
| **Delete the consumer group** | Delete the group while it is idle | Destructive — group metadata is gone |

The **replay mode** built into this app (see section below) is the cleanest option: it re-reads
messages in that one JVM run without touching the committed offset on the broker, so the next
normal restart picks up only new messages again.

---

## Message Replay

The consumer implements `ConsumerSeekAware`, which provides a `ConsumerSeekCallback` per partition at the moment partitions are assigned — **before the first `poll()`**. This allows seeking to any position before consumption begins.

Replay is controlled by the `kafka.replay.mode` property (default: `none`).

### Replay Modes

| Mode | Description |
|---|---|
| `none` | Normal mode — consume from last committed offset |
| `beginning` | Replay all messages from offset 0 |
| `timestamp` | Replay messages from a specific point in time |
| `offset` | Replay from a specific partition + offset |

---

### How to Run in Replay Mode

Replay mode is activated by passing arguments at startup. No code changes needed.

#### 1. Via Gradle `bootRun` (Spring application arguments)

```bash
# Replay from beginning
./gradlew bootRun --args='--kafka.replay.mode=beginning'

# Replay from a timestamp (epoch milliseconds)
./gradlew bootRun --args='--kafka.replay.mode=timestamp --kafka.replay.timestamp=1713456789000'

# Replay from a specific partition + offset
./gradlew bootRun --args='--kafka.replay.mode=offset --kafka.replay.partition=0 --kafka.replay.offset=5'
```

#### 2. Via Gradle `bootRun` (JVM system properties)

Thanks to `bootRun { systemProperties System.properties }` in `build.gradle`, `-D` args are forwarded to the app process:

```bash
./gradlew bootRun -Dkafka.replay.mode=beginning
./gradlew bootRun -Dkafka.replay.mode=timestamp -Dkafka.replay.timestamp=1713456789000
./gradlew bootRun -Dkafka.replay.mode=offset -Dkafka.replay.partition=0 -Dkafka.replay.offset=5
```

#### 3. Via IDE Run Configuration (Eclipse / IntelliJ)

- **Program arguments** field: `--kafka.replay.mode=beginning`
- **VM arguments** field: `-Dkafka.replay.mode=beginning`

#### 4. Via JAR

```bash
java -jar kafka-order-consumer.jar --kafka.replay.mode=beginning
java -Dkafka.replay.mode=beginning -jar kafka-order-consumer.jar
```

---

### How Property Resolution Works

`@Value("${kafka.replay.mode:none}")` is resolved by Spring's `Environment` in this priority order:

1. JVM system properties (`-D`)
2. Application arguments (`--`)
3. OS environment variables
4. `application.properties`
5. Default value (`:none`)

---

## Dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-kafka` | Kafka consumer support |
| `spring-boot-starter-json` | Jackson 3.x (`tools.jackson`) required by `JacksonJsonDeserializer` |
| `jspecify` | Null-safety annotations (`@NullMarked`, `@Nullable`) |
