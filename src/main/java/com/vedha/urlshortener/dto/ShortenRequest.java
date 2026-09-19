package com.vedha.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShortenRequest {

    @NotBlank(message = "longUrl must not be blank")
    private String longUrl;

    // Bound (3-30 chars) must match the redirect route's path pattern in
    // RedirectController/SecurityConfig - a longer alias would save fine but
    // then never match the GET /{shortCode} route, making the link dead on arrival.
    @Pattern(regexp = "^$|^[a-zA-Z0-9_-]{3,30}$", message = "customAlias must be 3-30 characters: letters, digits, underscore, hyphen only")
    private String customAlias;

    private Integer expiresInDays;

    private String createdBy;
}
