package com.example.hapjugakapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HapjugakApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HapjugakApiApplication.class, args);
    }
}