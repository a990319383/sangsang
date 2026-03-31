package com.sangsang.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sangsang.demo.mapper")
public class EncryptorDbApp {

    public static void main(String[] args) {
        SpringApplication.run(EncryptorDbApp.class, args);
    }
}
