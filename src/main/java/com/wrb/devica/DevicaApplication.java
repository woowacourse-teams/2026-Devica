package com.wrb.devica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class DevicaApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevicaApplication.class, args);
	}

}
