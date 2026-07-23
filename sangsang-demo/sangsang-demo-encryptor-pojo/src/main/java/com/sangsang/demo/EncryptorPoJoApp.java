package com.sangsang.demo;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sangsang.demo.mapper")
@Slf4j
public class EncryptorPoJoApp {

    public static void main(String[] args) {
        log.info("【sangsang】请确保将sangsang-demo-common的application-common.properties配置文件的数据库地址切换成自己的");
        SpringApplication.run(EncryptorPoJoApp.class, args);
        log.info("【sangsang】<demo-encryptor-pojo> 启动成功，可以访问 localhost:8002 查看效果，结合F12查看接口写法效果更佳");
    }
}
