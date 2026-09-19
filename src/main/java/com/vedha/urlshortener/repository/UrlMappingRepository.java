package com.vedha.urlshortener.repository;

import com.vedha.urlshortener.entity.UrlMapping;
import com.vedha.urlshortener.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCodeAndActiveTrue(String shortCode);
    boolean existsByShortCode(String shortCode);
    List<UrlMapping> findByOwnerAndActiveTrue(User owner);
}
