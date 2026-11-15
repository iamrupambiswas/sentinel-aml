package com.sentinel.aml.alert_case_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan com.sentinel.aml so common-lib's LoggingAspect and GlobalExceptionHandler are picked up
@SpringBootApplication(scanBasePackages = "com.sentinel.aml")
public class AlertCaseServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertCaseServiceApplication.class, args);
	}

}
