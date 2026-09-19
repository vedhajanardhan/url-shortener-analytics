package com.vedha.urlshortener.service;

import com.vedha.urlshortener.exception.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidationServiceTest {

    private UrlValidationService service;

    @BeforeEach
    void setUp() {
        service = new UrlValidationService();
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
    }

    @Test
    void acceptsValidHttpsUrl() {
        assertDoesNotThrow(() -> service.validate("https://example.com/some/path?x=1"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(InvalidUrlException.class, () -> service.validate(""));
        assertThrows(InvalidUrlException.class, () -> service.validate(null));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(InvalidUrlException.class, () -> service.validate("javascript:alert(1)"));
        assertThrows(InvalidUrlException.class, () -> service.validate("ftp://example.com/file"));
        assertThrows(InvalidUrlException.class, () -> service.validate("data:text/html,<script>alert(1)</script>"));
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(InvalidUrlException.class, () -> service.validate("http:///path-with-no-host"));
    }

    @Test
    void rejectsSelfReferentialUrl() {
        assertThrows(InvalidUrlException.class, () -> service.validate("http://localhost:8080/someShortCode"));
    }

    @Test
    void rejectsMalformedUri() {
        assertThrows(InvalidUrlException.class, () -> service.validate("http://exa mple.com/space in host"));
    }
}
