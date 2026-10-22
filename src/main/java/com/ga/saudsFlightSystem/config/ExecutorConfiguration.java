package com.ga.saudsFlightSystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService applicationExecutor() {
        return Executors.newFixedThreadPool(5);
    }
}
