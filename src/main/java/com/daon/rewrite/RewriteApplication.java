package com.daon.rewrite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RewriteApplication {

	public static void main(String[] args) {
		SpringApplication.run(RewriteApplication.class, args);
	}

}
