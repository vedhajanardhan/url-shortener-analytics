package com.vedha.urlshortener.exception;

public class AliasAlreadyTakenException extends RuntimeException {
    public AliasAlreadyTakenException(String alias) {
        super("Custom alias already in use: " + alias);
    }
}
