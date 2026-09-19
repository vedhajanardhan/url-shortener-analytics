package com.vedha.urlshortener.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "username must be 3-50 characters: letters, digits, underscore only")
    private String username;

    @NotBlank
    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "password must be at least 8 characters")
    private String password;
}
