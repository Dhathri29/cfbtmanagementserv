package com.paypal.sre.cfbt.management.rest;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

/**
 * This is your Raptor Spring Boot main class.
 *
 * <strong>Important:</strong> All of its annotations are necessary, please do not remove them
 */
@ComponentScan("com.paypal.sre.cfbt.management")
@EnableAutoConfiguration
public class RaptorApplication extends SpringBootServletInitializer {

	public static void main(String[] args) throws IOException {
		SpringApplication.run(RaptorApplication.class);
	}

}
