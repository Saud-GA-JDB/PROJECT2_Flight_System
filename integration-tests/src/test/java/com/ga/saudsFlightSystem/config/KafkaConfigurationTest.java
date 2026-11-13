package com.ga.saudsFlightSystem.config;

import com.ga.saudsFlightSystem.producer.KafkaProducer;
import com.ga.saudsFlightSystem.support.NotificationTestConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaProducer.class, KafkaTopicConfig.class)
            .withPropertyValues("spring.kafka.admin.auto-create=false");

    @Test
    void bootWiresProducerMapperAndTopicWithoutConnectingToABroker() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KafkaProducer.class).hasSingleBean(KafkaTemplate.class);
            NewTopic topic = context.getBean(NewTopic.class);
            assertThat(topic.name()).isEqualTo("aviation-notification-topic");
            assertThat(topic.numPartitions()).isEqualTo(1);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
            assertThat(topic.configs()).containsEntry("retention.ms", "86400000").containsEntry("cleanup.policy", "delete");
        });
    }

    @Test
    void existingFixturesSelectTheSubstituteProducer() {
        contextRunner.withUserConfiguration(NotificationTestConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KafkaProducer.class)).isSameAs(context.getBean("fixtureKafkaProducer"));
        });
    }
}
