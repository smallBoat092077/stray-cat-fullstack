package com.straycat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.straycat.mapper")
@EnableScheduling
public class StrayCatApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrayCatApplication.class, args);
    }
}
