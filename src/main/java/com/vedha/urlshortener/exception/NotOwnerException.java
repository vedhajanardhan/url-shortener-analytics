package com.vedha.urlshortener.exception;

public class NotOwnerException extends RuntimeException {
    public NotOwnerException(String shortCode) {
        super("You do not own the URL with short code: " + shortCode);
    }
}
