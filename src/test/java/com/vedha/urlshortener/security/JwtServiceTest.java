package com.vedha.urlshortener.security;

import com.vedha.urlshortener.entity.Role;
import com.vedha.urlshortener.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-at-least-32-characters-long-1234", 3600000L);
        user = User.builder().id(1L).username("vedha").role(Role.USER).enabled(true).build();
    }

    @Test
    void generatesTokenAndExtractsUsername() {
        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertEquals("vedha", jwtService.extractUsername(token));
    }

    @Test
    void validatesTokenForCorrectUser() {
        String token = jwtService.generateToken(user);
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void rejectsTokenForDifferentUser() {
        String token = jwtService.generateToken(user);
        User otherUser = User.builder().id(2L).username("someoneElse").role(Role.USER).enabled(true).build();
        assertFalse(jwtService.isTokenValid(token, otherUser));
    }

    @Test
    void rejectsShortSigningKey() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 3600000L));
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtService shortLivedJwtService = new JwtService("test-secret-key-at-least-32-characters-long-1234", 1L);
        String token = shortLivedJwtService.generateToken(user);
        Thread.sleep(50);
        assertFalse(shortLivedJwtService.isTokenValid(token, user));
    }
}
