package com.vedha.urlshortener.dto;

import lombok.Data;

@Data
public class UpdateUrlRequest {
    private String longUrl;
    private Integer expiresInDays;
}
