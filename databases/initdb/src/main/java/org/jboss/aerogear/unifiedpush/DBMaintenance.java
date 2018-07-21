package org.jboss.aerogear.unifiedpush;

import org.jboss.aerogear.unifiedpush.jpa.JPAConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DBMaintenance {
	public static void main(final String[] args) {
		// Initialize spring context to create DB schema
		AnnotationConfigApplicationContext applicationContext = null;

		System.out.println("Using config path from " + System.getProperty("aerobase.config.dir"));
		
		applicationContext = createApplicationContext();

		if (null != applicationContext) {
			applicationContext.close();
		}

		System.exit(0);
	}

	public static AnnotationConfigApplicationContext createApplicationContext() {
		final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(JPAConfig.class);
		ctx.refresh();

		return ctx;
	}

	public static AnnotationConfigApplicationContext inititializeApplicationContext() {
		final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
		ctx.register(JPAConfig.class);
		ctx.refresh();

		return ctx;
	}

}
