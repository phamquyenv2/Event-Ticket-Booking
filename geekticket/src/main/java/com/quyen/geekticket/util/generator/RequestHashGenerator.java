package com.quyen.geekticket.util.generator;

import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import java.util.Locale;

@Component
public class RequestHashGenerator {

    public String generate(CreateBookingRequest request) {
        return sha256(canonicalize(request));
    }

    String canonicalize(CreateBookingRequest request) {
        String normalizedVoucher = request != null && request.getVoucherCode() != null
                && !request.getVoucherCode().isBlank()
                ? request.getVoucherCode().trim().toUpperCase(Locale.ROOT)
                : "null";

        StringBuilder canonical = new StringBuilder("concertId=")
                .append(request != null ? request.getConcertId() : null)
                .append(";voucherCode=")
                .append(normalizedVoucher)
                .append(";items=");

        List<BookingItemRequest> items = request != null ? request.getItems() : null;
        if (items == null) {
            return canonical.append("null").toString();
        }

        items.stream()
                .sorted(Comparator
                        .comparing((BookingItemRequest item) ->
                                        item != null ? item.getTicketCategoryId() : null,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(item -> item != null ? item.getQuantity() : null,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(item -> canonical
                        .append('[')
                        .append(item != null ? item.getTicketCategoryId() : null)
                        .append(':')
                        .append(item != null ? item.getQuantity() : null)
                        .append(']'));
        return canonical.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
