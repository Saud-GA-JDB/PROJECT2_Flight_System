package com.ga.saudsFlightSystem.config;

import com.ga.common.config.SchedulingConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulingConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "true", "false"})
    void registersSchedulingOnlyWhenEnabled(String setting) {
        try (var context = new AnnotationConfigApplicationContext()) {
            if (!setting.equals("default")) {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("test", Map.of("app.scheduling.enabled", setting)));
            }
            context.register(SchedulingConfiguration.class);
            context.refresh();
            assertEquals(!setting.equals("false"),
                    !context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).isEmpty());
        }
    }
}
