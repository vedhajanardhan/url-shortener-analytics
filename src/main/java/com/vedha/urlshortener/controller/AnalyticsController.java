package com.vedha.urlshortener.controller;

import com.vedha.urlshortener.dto.AnalyticsSummaryResponse;
import com.vedha.urlshortener.entity.User;
import com.vedha.urlshortener.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Click analytics for URLs you own")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{shortCode}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get click analytics for a URL you own")
    public AnalyticsSummaryResponse getAnalytics(@PathVariable String shortCode,
                                                  @RequestParam(defaultValue = "30") int daysBack,
                                                  @AuthenticationPrincipal User currentUser) {
        return analyticsService.getSummary(shortCode, daysBack, currentUser);
    }
}
