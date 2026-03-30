package dei.googol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the Googol web application.
 *
 * <p>The {@code @SpringBootApplication} annotation triggers component scanning
 * starting from the {@code dei.googol} package, which covers all web controllers,
 * services, and configuration classes.
 *
 * <p>The web server acts as a thin client layer on top of the RMI infrastructure:
 * it connects to the {@link dei.googol.core.Gateway} via Java RMI and exposes the
 * same functionality through a browser-accessible HTTP/WebSocket interface.
 */
@SpringBootApplication
@EnableScheduling
public class GoogolSpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoogolSpringBootApplication.class, args);
    }
}
