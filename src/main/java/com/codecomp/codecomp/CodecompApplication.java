package com.codecomp.codecomp;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class CodecompApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodecompApplication.class, args);
	}

}
