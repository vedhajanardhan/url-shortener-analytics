package com.vedha.urlshortener.service;

import lombok.Data;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Thin wrapper around uap-java to turn a raw User-Agent header into
 * dashboard-friendly device/browser/OS labels.
 */
@Service
public class UserAgentParsingService {

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUserAgent("Unknown", "Unknown", "Unknown");
        }
        Client client = parser.parse(userAgent);

        String deviceType = classifyDevice(client.device.family, userAgent);
        String browser = client.userAgent.family != null ? client.userAgent.family : "Unknown";
        String os = client.os.family != null ? client.os.family : "Unknown";

        return new ParsedUserAgent(deviceType, browser, os);
    }

    private String classifyDevice(String deviceFamily, String rawUa) {
        String ua = rawUa.toLowerCase();
        if (ua.contains("mobile") || "iPhone".equalsIgnoreCase(deviceFamily)) return "Mobile";
        if (ua.contains("tablet") || "iPad".equalsIgnoreCase(deviceFamily)) return "Tablet";
        if (deviceFamily != null && !deviceFamily.equalsIgnoreCase("Other")) return "Mobile";
        return "Desktop";
    }

    @Data
    public static class ParsedUserAgent {
        private final String deviceType;
        private final String browser;
        private final String os;
    }
}
