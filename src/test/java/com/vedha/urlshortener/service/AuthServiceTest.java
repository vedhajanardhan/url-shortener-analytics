package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.AuthResponse;
import com.vedha.urlshortener.dto.LoginRequest;
import com.vedha.urlshortener.dto.RegisterRequest;
import com.vedha.urlshortener.entity.Role;
import com.vedha.urlshortener.entity.User;
import com.vedha.urlshortener.exception.EmailAlreadyRegisteredException;
import com.vedha.urlshortener.exception.UsernameAlreadyTakenException;
import com.vedha.urlshortener.repository.UserRepository;
import com.vedha.urlshortener.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "expirationMs", 3600000L);
    }

    @Test
    void register_rejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("vedha");
        request.setEmail("vedha@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("vedha")).thenReturn(true);

        assertThrows(UsernameAlreadyTakenException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("vedha");
        request.setEmail("vedha@example.com");
        request.setPassword("password123");

        when(userRepository.existsByUsername("vedha")).thenReturn(false);
        when(userRepository.existsByEmail("vedha@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class, () -> authService.register(request));
    }

    @Test
    void register_hashesPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("vedha");
        request.setEmail("vedha@example.com");
        request.setPassword("plaintext-password");

        when(userRepository.existsByUsername("vedha")).thenReturn(false);
        when(userRepository.existsByEmail("vedha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-value");
        when(jwtService.generateToken(any(User.class))).thenReturn("fake-jwt");

        AuthResponse response = authService.register(request);

        assertEquals("fake-jwt", response.getToken());
        assertEquals("vedha", response.getUsername());
        verify(userRepository).save(argThat(u ->
                u.getPasswordHash().equals("hashed-value") &&
                u.getRole() == Role.USER &&
                u.isEnabled()));
        // Never persist the raw password anywhere
        verify(userRepository, never()).save(argThat(u -> "plaintext-password".equals(u.getPasswordHash())));
    }

    @Test
    void login_delegatesToAuthenticationManagerAndReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("vedha");
        request.setPassword("password123");

        User user = User.builder().id(1L).username("vedha").role(Role.USER).enabled(true).build();
        when(userRepository.findByUsername("vedha")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("fake-jwt");

        AuthResponse response = authService.login(request);

        assertEquals("fake-jwt", response.getToken());
        verify(authenticationManager).authenticate(any());
    }
}
