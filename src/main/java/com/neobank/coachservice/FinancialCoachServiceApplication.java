package com.neobank.coachservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinancialCoachServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialCoachServiceApplication.class, args);
    }

}
