package com.paypal.sre.cfbt.management.rest.init;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * This class can be used as a hook for post application initialization. At the
 * moment the postInitialization method is called, any Spring bean will be
 * already available, which means any Spring bean can be injected to this class
 * and used them in the postInitialization method.
 */
@Component
public class ApplicationListener {

	/**
	 * A listener method notifying that the application has started and all
	 * Spring beans are available.
	 */
	@EventListener
	public void postInitialization(ContextRefreshedEvent event) {
	}

}
