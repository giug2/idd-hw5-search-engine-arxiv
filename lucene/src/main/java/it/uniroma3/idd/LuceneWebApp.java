package it.uniroma3.idd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LuceneWebApp {

    public static void main(String[] args) {
        SpringApplication.run(LuceneWebApp.class, args);
    }
}

// mvn spring-boot:run

/*possibili input tabelle:
caption comparison
VoiceLDM AND 17.2 (output solo 1 tabella)

*/