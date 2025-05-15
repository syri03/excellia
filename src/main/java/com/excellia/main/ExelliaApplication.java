package com.excellia.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(scanBasePackages = "com.excellia")
public class ExelliaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExelliaApplication.class, args);
    }
    
  

}
