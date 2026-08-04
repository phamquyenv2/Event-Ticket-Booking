package com.quyen.geekticket.util.generator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class BookingCodeGenerator {

    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int RANDOM_CODE_LENGTH = 6;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final SecureRandom random = new SecureRandom();

    public String generateCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        StringBuilder sb = new StringBuilder(RANDOM_CODE_LENGTH);
        for (int i = 0; i < RANDOM_CODE_LENGTH; i++) {
            int randomIndex = random.nextInt(ALPHA_NUMERIC.length());
            sb.append(ALPHA_NUMERIC.charAt(randomIndex));
        }
        return "BK-" + datePart + "-" + sb.toString();
    }
}
