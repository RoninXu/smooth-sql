package com.smoothsql;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.smoothsql.mapper")
@EnableConfigurationProperties
public class SmoothSqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmoothSqlApplication.class, args);
    }

}