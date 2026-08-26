package com.group11.compostsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CompostsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompostsystemApplication.class, args);
	}

}
