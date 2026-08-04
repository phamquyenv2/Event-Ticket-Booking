package com.quyen.geekticket.util.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingCodeGeneratorTest {

    private final BookingCodeGenerator generator = new BookingCodeGenerator();

    @Test
    @DisplayName("generateCode should produce code matching BK-YYYYMMDD-XXXXXX format")
    void generateCode_matchesExpectedFormat() {
        String code = generator.generateCode();

        assertThat(code).isNotNull();
        assertThat(code).matches("^BK-\\d{8}-[A-Z0-9]{6}$");
    }

    @Test
    @DisplayName("generateCode should contain current UTC date in YYYYMMDD format")
    void generateCode_containsCurrentUtcDate() {
        String code = generator.generateCode();
        String todayStr = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        assertThat(code).contains(todayStr);
    }

    @Test
    @DisplayName("generateCode should produce unique values on sequential invocations")
    void generateCode_producesUniqueValues() {
        int count = 100;
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < count; i++) {
            codes.add(generator.generateCode());
        }

        assertThat(codes).hasSize(count);
    }
}
