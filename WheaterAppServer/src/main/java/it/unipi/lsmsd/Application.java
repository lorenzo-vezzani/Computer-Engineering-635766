package it.unipi.lsmsd;

import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {

    // Set the default timezone in the main application class
    // For consistent handling of Date using UTC
    @PostConstruct
    public void init(){
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        final Logger logger = LoggerFactory.getLogger(Application.class);

        try {
            SpringApplication.run(Application.class, args);
            logger.info("Successful Application Run \n");
              
        } catch (Exception e) {
            logger.error("Error running the Application: ", e);
        }
    }
}
