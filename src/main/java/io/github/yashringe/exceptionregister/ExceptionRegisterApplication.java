package io.github.yashringe.exceptionregister;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExceptionRegisterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionRegisterApplication.class, args);
    }
}
