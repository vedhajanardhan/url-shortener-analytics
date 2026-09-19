package com.vedha.urlshortener.util;

/**
 * Encodes a numeric auto-increment DB id into a short, URL-safe Base62 string.
 * Using the DB id (rather than a random string) guarantees uniqueness with zero
 * collision checks and zero extra round-trips.
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {}

    public static String encode(long value) {
        if (value == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            sb.append(ALPHABET.charAt((int) (v % BASE)));
            v /= BASE;
        }
        return sb.reverse().toString();
    }
}
