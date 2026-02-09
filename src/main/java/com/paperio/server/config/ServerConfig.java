package com.paperio.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ServerConfig {

    @Bean(name = "gameExecutor")
    public ExecutorService gameExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}