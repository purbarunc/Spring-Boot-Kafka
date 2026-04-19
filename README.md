# Spring-Boot-Kafka

## Kafka with KRaft (No Zookeeper) — Docker Setup

Latest Apache Kafka running in KRaft mode (Kafka Raft Metadata mode), which replaces Zookeeper entirely.

## Services

| Service     | Image                          | Port  | Description               |
|-------------|--------------------------------|-------|---------------------------|
| `kafka`     | `apache/kafka:latest`          | 9092  | Kafka broker (KRaft mode) |
| `kafka-ui`  | `provectuslabs/kafka-ui:latest`| 8080  | Web UI for Kafka           |

## Quick Start

```bash
# Start all services
docker compose up -d

# Check status
docker compose ps

# View Kafka logs
docker compose logs -f kafka
```

Open Kafka UI at: http://localhost:8080

## Useful Kafka Commands

All commands run inside the Kafka container:

```bash
# Enter the container
docker exec -it kafka-kraft bash

# List topics
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Create a topic
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic my-topic --partitions 3 --replication-factor 1

# Describe a topic
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic my-topic

# Produce messages
/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic my-topic

# Consume messages (from beginning)
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic my-topic --from-beginning

# Delete a topic
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic my-topic
```

## Connect from Your App

Use `localhost:9092` as the bootstrap server from your host machine.

```
bootstrap.servers=localhost:9092
```

## Stop & Clean Up

```bash
# Stop services (keeps data)
docker compose down

# Stop and remove all data (volumes)
docker compose down -v
```

## KRaft Configuration Notes

- No Zookeeper required — this node acts as both broker and controller (`KAFKA_PROCESS_ROLES: broker,controller`)
- `CLUSTER_ID` is a fixed base64-encoded UUID — change it if you need a fresh cluster
- Data is persisted in the `kafka-data` Docker volume
