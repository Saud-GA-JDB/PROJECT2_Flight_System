package com.ga.saudsFlightSystem.consumer;

import com.ga.common.events.NotificationEvent;
import com.ga.notification.config.KafkaConsumerConfig;
import com.ga.notification.consumer.KafkaConsumer;
import com.ga.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaRecordCommitTest {
    @Test
    @Timeout(20)
    void recordOffsetIsCommittedOnlyAfterTheDispatchReturns() throws InterruptedException {
        String topic = "aviation-notification-topic";
        TopicPartition partition = new TopicPartition(topic, 0);
        CountDownLatch dispatchStarted = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        AtomicLong committedOffset = new AtomicLong(-1);
        MockConsumer<String, String> broker = new MockConsumer<>("earliest") {
            @Override
            public synchronized void commitSync(Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
                super.commitSync(offsets, timeout);
                OffsetAndMetadata offset = offsets.get(partition);
                if (offset != null) {
                    committedOffset.set(offset.offset());
                    if (offset.offset() == 1) committed.countDown();
                }
            }
        };
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        String message = mapper.writeValueAsString(new NotificationEvent(
                "event-1", 25L, null, "FLIGHT_CREATED", "Flight GF25 added"));
        broker.updateBeginningOffsets(Map.of(partition, 0L));
        broker.updateEndOffsets(Map.of(partition, 1L));
        broker.schedulePollTask(() -> {
            broker.rebalance(List.of(partition));
            broker.addRecord(new ConsumerRecord<>(topic, 0, 0L, "user:25", message));
        });
        NotificationService notifications = mock(NotificationService.class);
        doAnswer(invocation -> {
            dispatchStarted.countDown();
            if (!releaseDispatch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test dispatch timed out");
            return null;
        }).when(notifications).sendToUser(anyLong(), anyString(), any());
        KafkaConsumer listener = new KafkaConsumer(notifications, mapper);
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        var factory = config.kafkaListenerContainerFactory();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config.consumerConfigs()) {
            @Override
            public Consumer<String, String> createConsumer(String groupId, String prefix, String suffix, Properties properties) {
                return broker;
            }
        });
        var container = factory.createContainer(topic);
        container.setBeanName("record-commit-test");
        container.getContainerProperties().setMessageListener(
                (MessageListener<String, String>) record -> listener.consume(record.value()));
        try {
            container.start();
            assertTrue(dispatchStarted.await(5, TimeUnit.SECONDS), "Listener did not receive the mock record");
            assertTrue(committedOffset.get() < 1, "Record was committed before dispatch returned");
            releaseDispatch.countDown();
            assertTrue(committed.await(5, TimeUnit.SECONDS), "Record was not committed after dispatch");
            verify(notifications).sendToUser(25L, "FLIGHT_CREATED", "Flight GF25 added");
        } finally {
            releaseDispatch.countDown();
            container.stop();
        }
    }
}
