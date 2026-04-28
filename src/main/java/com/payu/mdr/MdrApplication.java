package com.payu.mdr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MdrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdrApplication.class, args);
    }
}