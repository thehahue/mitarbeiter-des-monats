package at.bbrz.mitarbeiter;

import at.bbrz.mitarbeiter.service.MitarbeiterService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MitarbeiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(MitarbeiterApplication.class, args);
    }

    @Bean
    ApplicationRunner beispieldaten(MitarbeiterService service) {
        return args -> service.initializeExamples();
    }
}
