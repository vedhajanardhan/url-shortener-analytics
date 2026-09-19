package com.vedha.urlshortener.exception;

public class ShortCodeNotFoundException extends RuntimeException {
    public ShortCodeNotFoundException(String shortCode) {
        super("No active URL found for short code: " + shortCode);
    }
}
