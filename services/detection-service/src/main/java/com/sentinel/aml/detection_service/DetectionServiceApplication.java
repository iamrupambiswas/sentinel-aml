package com.sentinel.aml.detection_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sentinel.aml")
public class DetectionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DetectionServiceApplication.class, args);
	}

}
