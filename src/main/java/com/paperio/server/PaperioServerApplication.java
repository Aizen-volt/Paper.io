package com.paperio.server;

import com.paperio.server.config.GameProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GameProperties.class)
public class PaperioServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperioServerApplication.class, args);
    }

}
