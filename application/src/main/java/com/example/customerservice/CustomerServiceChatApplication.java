package com.example.customerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableAsync
@MapperScan(
        "com.example.customerservice.mapper"
)
public class CustomerServiceChatApplication {

    public static void main(
            String[] args
    ) {
        SpringApplication.run(
                CustomerServiceChatApplication.class,
                args
        );
    }
}
