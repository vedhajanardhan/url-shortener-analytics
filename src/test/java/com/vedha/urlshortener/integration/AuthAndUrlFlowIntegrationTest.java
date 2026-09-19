package com.vedha.urlshortener.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedha.urlshortener.repository.ClickEventRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage of the flow that matters most: register -> login ->
 * create a URL (owned) -> redirect (tracked async via Kafka) -> analytics
 * (ownership-enforced). Uses real MySQL and Kafka via Testcontainers so Flyway
 * migrations and the actual Kafka consumer pipeline are exercised, not mocked.
 *
 * Redis is deliberately NOT containerized here: the app has no Redis available
 * in this test, so this also doubles as a real-world exercise of the
 * Redis-unavailable fallback path documented in RedisFallbackTest, end-to-end
 * through the actual REST API instead of in isolation.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthAndUrlFlowIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("urlshortener_it")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.jwt.secret", () -> "integration-test-secret-key-must-be-long-enough-1234567890");
        // Point Redis at a guaranteed-unreachable port to exercise the fallback
        // path end-to-end, matching production behavior during a Redis outage.
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "1");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClickEventRepository clickEventRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String registerAndGetToken(String username, String email) throws Exception {
        Map<String, String> body = Map.of(
                "username", username,
                "email", email,
                "password", "SecurePass123");

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/register", body, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = mapper.readTree(response.getBody());
        return json.get("token").asText();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void fullFlow_registerCreateRedirectTrackAnalytics() throws Exception {
        String token = registerAndGetToken("integrationuser", "integration@example.com");

        // Create a short URL
        Map<String, String> createBody = Map.of("longUrl", "https://example.com/integration-test-path");
        HttpEntity<Map<String, String>> createRequest = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                baseUrl() + "/api/urls", createRequest, String.class);

        assertEquals(HttpStatus.OK, createResponse.getStatusCode());
        JsonNode created = mapper.readTree(createResponse.getBody());
        String shortCode = created.get("shortCode").asText();
        assertNotNull(shortCode);

        // Hit the redirect endpoint (public, no auth needed)
        ResponseEntity<Void> redirectResponse = restTemplate.exchange(
                baseUrl() + "/" + shortCode, HttpMethod.GET, HttpEntity.EMPTY, Void.class);
        assertEquals(HttpStatus.FOUND, redirectResponse.getStatusCode());
        assertEquals("https://example.com/integration-test-path",
                redirectResponse.getHeaders().getLocation().toString());

        // Click tracking is async (Kafka producer -> consumer -> MySQL) - poll
        // instead of asserting immediately.
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() ->
                        assertEquals(1, clickEventRepository.countByShortCode(shortCode)));

        // Analytics reflects the tracked click - falls back to the MySQL count
        // since Redis is unreachable in this test (see overrideProperties above)
        ResponseEntity<String> analyticsResponse = restTemplate.exchange(
                baseUrl() + "/api/analytics/" + shortCode, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), String.class);

        assertEquals(HttpStatus.OK, analyticsResponse.getStatusCode());
        JsonNode analytics = mapper.readTree(analyticsResponse.getBody());
        assertEquals(1, analytics.get("totalClicks").asLong());
        assertEquals("mysql-durable", analytics.get("totalClicksSource").asText());
    }

    @Test
    void anotherUser_cannotSeeAnalyticsForUrlTheyDontOwn() throws Exception {
        String ownerToken = registerAndGetToken("owneruser", "owner@example.com");
        String strangerToken = registerAndGetToken("strangeruser", "stranger@example.com");

        Map<String, String> createBody = Map.of("longUrl", "https://example.com/private-path");
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                baseUrl() + "/api/urls", new HttpEntity<>(createBody, authHeaders(ownerToken)), String.class);
        JsonNode created = mapper.readTree(createResponse.getBody());
        String shortCode = created.get("shortCode").asText();

        ResponseEntity<String> analyticsAsStranger = restTemplate.exchange(
                baseUrl() + "/api/analytics/" + shortCode, HttpMethod.GET,
                new HttpEntity<>(authHeaders(strangerToken)), String.class);

        assertEquals(HttpStatus.FORBIDDEN, analyticsAsStranger.getStatusCode());
    }

    @Test
    void unauthenticatedRequest_toCreateUrl_isRejected() {
        Map<String, String> createBody = Map.of("longUrl", "https://example.com/no-auth");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/urls", new HttpEntity<>(createBody, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void invalidUrl_isRejectedWithBadRequest() {
        Map<String, String> createBody = Map.of("longUrl", "javascript:alert(1)");
        // Registration not strictly needed for a validation-failure test, but
        // keeps this realistic (an authenticated user submitting a bad URL).
        String token;
        try {
            token = registerAndGetToken("validationuser", "validation@example.com");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/urls", new HttpEntity<>(createBody, authHeaders(token)), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
