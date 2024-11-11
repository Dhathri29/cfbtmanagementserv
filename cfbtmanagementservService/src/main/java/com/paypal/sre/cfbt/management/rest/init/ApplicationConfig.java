package com.paypal.sre.cfbt.management.rest.init;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import org.springframework.stereotype.Component;

/**
 * This is a JAX-RS compliant way to map your JAX-RS root context for your
 * application. The resources will be mapped to the ApplicationPath defined 
 * in this annotation and the @PATH annotation will be appended to it.
 * ApplicationPath that serves as the base URI is structured based on PPaaS standards.
 */
@ApplicationPath("/v2/cfbt/")
@Component
public class ApplicationConfig extends Application {
}
