package br.com.ctcore;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CtcoreApplication {

    static {
        // D4: toda data e hora em UTC; o fuso da unidade só aparece na exibição.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CtcoreApplication.class, args);
    }
}
