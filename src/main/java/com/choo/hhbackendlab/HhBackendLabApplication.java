package com.choo.hhbackendlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HhBackendLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(HhBackendLabApplication.class, args);
    }

}
