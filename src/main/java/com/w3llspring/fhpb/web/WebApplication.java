package com.w3llspring.fhpb.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.w3llspring.fhpb.web.db")
@EntityScan(basePackages = "com.w3llspring.fhpb.web.model")
@org.springframework.scheduling.annotation.EnableScheduling
public class WebApplication {

  public static void main(String[] args) {
    SpringApplication.run(WebApplication.class, args);
  }
}
