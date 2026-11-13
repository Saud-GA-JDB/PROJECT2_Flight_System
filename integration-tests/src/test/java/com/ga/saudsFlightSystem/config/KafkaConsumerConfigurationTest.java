package com.ga.saudsFlightSystem.config;

import com.ga.notification.config.KafkaConsumerConfig;
import com.ga.notification.consumer.KafkaConsumer;
import com.ga.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class KafkaConsumerConfigurationTest {
    @Test
    void bootRegistersTheRecordListenerWithTheCustomFactory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KafkaAutoConfiguration.class))
                .withUserConfiguration(KafkaConsumerConfig.class, KafkaConsumer.class, StoppedListeners.class)
                .withBean(NotificationService.class, () -> mock(NotificationService.class))
                .withPropertyValues("kafka.bootstrap-servers=localhost:9092", "spring.kafka.admin.auto-create=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(KafkaConsumer.class);
                    var registry = context.getBean(KafkaListenerEndpointRegistry.class);
                    assertEquals(1, registry.getListenerContainers().size());
                    var container = registry.getListenerContainers().iterator().next();
                    assertFalse(container.isRunning());
                    assertEquals("notification-group", container.getGroupId());
                    assertArrayEquals(new String[]{"aviation-notification-topic"}, container.getContainerProperties().getTopics());
                    assertEquals(ContainerProperties.AckMode.RECORD, container.getContainerProperties().getAckMode());
                    assertTrue(container.getContainerProperties().isSyncCommits());
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void unexpectedFailureGetsTwoRetriesThenIsSkipped(CapturedOutput output) {
        var container = config().kafkaListenerContainerFactory().createContainer("aviation-notification-topic");
        var handler = container.getCommonErrorHandler();
        Consumer<String, String> broker = mock(Consumer.class);
        MessageListenerContainer runningContainer = mock(MessageListenerContainer.class);
        when(runningContainer.isRunning()).thenReturn(true);
        var record = new ConsumerRecord<>("aviation-notification-topic", 0, 5L, "user:25", "private-payload");
        var failure = new IllegalStateException("private-error-detail");
        try {
            assertFalse(handler.handleOne(failure, record, broker, runningContainer));
            assertFalse(handler.handleOne(failure, record, broker, runningContainer));
            assertTrue(handler.handleOne(failure, record, broker, runningContainer));
            assertTrue(handler.isAckAfterHandle());
            assertTrue(output.getAll().contains("Notification skipped: topic=aviation-notification-topic, partition=0, offset=5"));
            assertFalse(output.getAll().contains("private-payload"));
            assertFalse(output.getAll().contains("private-error-detail"));
        } finally {
            handler.clearThreadState();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidMessageIsSkippedWithoutRetries() {
        var container = config().kafkaListenerContainerFactory().createContainer("aviation-notification-topic");
        var handler = container.getCommonErrorHandler();
        var record = new ConsumerRecord<>("aviation-notification-topic", 0, 7L, "user:25", "invalid-json");
        try {
            assertTrue(handler.handleOne(new IllegalArgumentException("Invalid notification JSON."), record,
                    mock(Consumer.class), mock(MessageListenerContainer.class)));
            assertTrue(handler.isAckAfterHandle());
        } finally {
            handler.clearThreadState();
        }
    }

    private KafkaConsumerConfig config() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        return config;
    }

    @Configuration
    static class StoppedListeners {
        @Bean
        static BeanPostProcessor stopKafkaListenersForTests() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                        factory.setAutoStartup(false);
                    }
                    return bean;
                }
            };
        }
    }
}
