package io.github.asyncflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AsyncFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsyncFlowApplication.class, args);
    }
}
