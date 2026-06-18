package com.sangsang.demo;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sangsang.demo.mapper")
@Slf4j
public class EncryptorDbApp {

    public static void main(String[] args) {
        SpringApplication.run(EncryptorDbApp.class, args);
        log.info("【sangsang】<demo-encryptor-db> 启动成功，可以访问 localhost:8001 查看效果，结合F12查看接口写法效果更佳");
    }
}
