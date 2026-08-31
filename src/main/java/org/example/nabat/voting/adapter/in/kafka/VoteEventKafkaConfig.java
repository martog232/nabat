package org.example.nabat.voting.adapter.in.kafka;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.nabat.events.vote.VoteChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * The consumer side of nabat-app. There is no producer: this service publishes its own events
 * in-process through the Event Publication Registry, and the only thing it takes off a broker
 * is what nabat-voting says about vote counts.
 *
 * <p>Values are Avro with a schema id in front of them. The id is resolved through the schema
 * registry into {@link VoteChanged}, generated from {@code src/main/avro/VoteChanged.avsc} —
 * a verbatim copy of nabat-voting's. Nothing here parses field names: if the two copies ever
 * diverge incompatibly, the registry refuses the producer's schema rather than letting this
 * side quietly read the wrong thing.
 *
 * <p>Absent entirely unless {@code nabat.kafka.enabled} is set — see {@link VoteEventListener}
 * for why that is a property of the topology rather than a feature switch.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "nabat.kafka.enabled", havingValue = "true")
public class VoteEventKafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(VoteEventKafkaConfig.class);

    /**
     * @param autoOffsetReset {@code earliest} by design. The tallies are absolute and keyed by
     *                        alert, so replaying the topic from the start converges on the
     *                        latest value for every alert — a new deployment rebuilds its
     *                        projection instead of waiting for the next vote on each alert.
     *                        It is also why the schema's compatibility level is BACKWARD: a
     *                        replay means reading messages written by older producers.
     */
    @Bean
    public ConsumerFactory<String, VoteChanged> voteEventConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset,
            @Value("${nabat.schema-registry.url}") String schemaRegistryUrl) {

        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Wrapped, so that a record this service cannot read — a schema id the registry does
        // not know, a corrupt payload — becomes a handled failure rather than an exception
        // thrown inside the poll loop, which the container can only answer by trying the same
        // record again forever.
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);

        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // Without this the deserialiser hands back a GenericRecord and every field access is
        // a string lookup that the compiler cannot check — which is most of what the schema
        // was adopted to stop.
        properties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VoteChanged>
            voteEventListenerContainerFactory(ConsumerFactory<String, VoteChanged> voteEventConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, VoteChanged> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(voteEventConsumerFactory);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    /**
     * Retries a few times, then logs the record and moves on.
     *
     * <p>Giving up is safe here for the same reason redelivery is: the tallies are absolute,
     * so the next vote on that alert writes the correct numbers regardless of what was
     * skipped. Not giving up is not safe — one unprocessable record would block its
     * partition, and with a single partition that is every alert's counts, indefinitely.
     */
    private DefaultErrorHandler errorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Giving up on record from {} partition {} offset {}: {}. The vote counts for "
                                + "that alert stay as they are until the next vote on it.",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()),
                new FixedBackOff(1_000L, 3L)
        );
        handler.setCommitRecovered(true);
        return handler;
    }
}
