package com.zm.skill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SkillCollectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillCollectorApplication.class, args);
    }
}
