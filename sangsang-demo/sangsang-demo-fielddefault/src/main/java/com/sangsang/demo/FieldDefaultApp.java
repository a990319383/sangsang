package com.sangsang.demo;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sangsang.demo.mapper")
@Slf4j
public class FieldDefaultApp {

    public static void main(String[] args) {
        SpringApplication.run(FieldDefaultApp.class, args);
        log.info("【sangsang】<demo-fielddefault> 启动成功，可以访问 localhost:8005 查看效果，结合F12查看接口写法效果更佳");
    }
}
