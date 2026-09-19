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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyTakenException(request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyRegisteredException(request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .enabled(true)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .expiresInMs(expirationMs)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates credential checking to Spring Security's AuthenticationManager
        // (uses the DaoAuthenticationProvider + BCrypt configured in SecurityConfig)
        // rather than comparing passwords by hand here.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished mid-request"));

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .username(user.getUsername())
                .expiresInMs(expirationMs)
                .build();
    }
}
