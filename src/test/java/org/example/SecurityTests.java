package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/survey_lab4_test}"
})
class SecurityTests {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PASSWORD = "TestAccount123!";

    private class Session {
        private final String authorization;
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        Session(String username, String password) {
            authorization = username == null ? null : "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }

        HttpResponse<String> raw(String method, String path, String body, String token) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/" + path))
                    .header("Content-Type", "application/json");
            if (authorization != null) builder.header("Authorization", authorization);
            if (token != null) builder.header("X-CSRF-TOKEN", token);
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }

        String csrf() throws Exception {
            var response = raw("GET", "csrf", null, null);
            assertEquals(200, response.statusCode());
            return json.readTree(response.body()).path("token").asText();
        }

        JsonNode call(String method, String path, String body, int status) throws Exception {
            String token = List.of("GET", "HEAD", "OPTIONS").contains(method) ? null : csrf();
            var response = raw(method, path, body, token);
            assertEquals(status, response.statusCode(), method + " " + path + ": " + response.body());
            assertFalse(response.body().contains("password_hash"));
            assertFalse(response.body().contains(PASSWORD));
            return response.body().isBlank() ? null : json.readTree(response.body());
        }
    }

    private Session admin() { return new Session("admin", "TestAdmin123!"); }
    private String username() { return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    private String accountBody(String name, String role) {
        return "{\"username\":\"" + name + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}";
    }

    private JsonNode account(String name, String role) throws Exception {
        return admin().call("POST", "users", accountBody(name, role), 201);
    }

    private long survey(Session author, long authorId) throws Exception {
        return author.call("POST", "surveys", "{\"title\":\"Security survey\",\"authorId\":" + authorId
                + ",\"endDate\":\"" + LocalDateTime.now().plusDays(1) + "\"}", 201).path("id").asLong();
    }

    private long question(Session author, long surveyId) throws Exception {
        return author.call("POST", "survey-questions", "{\"surveyId\":" + surveyId
                + ",\"text\":\"Question\",\"required\":true,\"options\":[\"Yes\",\"No\"]}", 201).path("id").asLong();
    }

    @Test
    void registrationChecksPasswordUniquenessAndNeverGrantsPrivileges() throws Exception {
        String name = username();
        Session anonymous = new Session(null, null);
        anonymous.call("POST", "auth/register", "{\"username\":\"" + name + "\",\"password\":\"weak\"}", 400);
        JsonNode registered = anonymous.call("POST", "auth/register",
                accountBody(name, "ADMIN").replace("}", ",\"enabled\":false}"), 201);
        assertEquals("USER", registered.path("role").asText());
        assertTrue(registered.path("enabled").asBoolean());
        assertFalse(registered.has("password"));
        assertFalse(registered.has("passwordHash"));
        anonymous.call("POST", "auth/register", accountBody(name.toUpperCase(), "USER"), 409);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class,
                registered.path("id").asLong());
        assertTrue(hash.startsWith("$2"));
        assertNotEquals(PASSWORD, hash);
        assertTrue(encoder.matches(PASSWORD, hash));
        Session user = new Session(name, PASSWORD);
        user.call("GET", "auth/me", null, 200);
        user.call("PATCH", "users/" + registered.path("id").asLong(), "{\"role\":\"ADMIN\"}", 403);
        user.call("POST", "users", accountBody(username(), "ADMIN"), 403);
        user.call("GET", "users", null, 403);
    }

    @Test
    void basicAuthenticationAndDisabledAccountReturn401WithoutSensitiveDetails() throws Exception {
        assertEquals(401, new Session(null, null).raw("GET", "surveys", null, null).statusCode());
        var wrong = new Session("admin", "wrong-password").raw("GET", "auth/me", null, null);
        assertEquals(401, wrong.statusCode());
        assertTrue(wrong.headers().firstValue("WWW-Authenticate").isPresent());
        assertFalse(wrong.body().contains("wrong-password"));
        assertFalse(wrong.body().contains("Exception"));
        String name = username();
        long id = account(name, "USER").path("id").asLong();
        Session user = new Session(name, PASSWORD);
        user.call("GET", "auth/me", null, 200);
        admin().call("PATCH", "users/" + id, "{\"enabled\":false}", 200);
        assertEquals(401, user.raw("GET", "auth/me", null, null).statusCode());
        admin().call("PATCH", "users/" + id, "{\"enabled\":true}", 200);
        user.call("GET", "auth/me", null, 200);
    }

    @Test
    void csrfIsRequiredForWritesAndBoundToTheClientSession() throws Exception {
        Session admin = admin();
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);
        String body = accountBody(username(), "USER");
        assertEquals(403, admin.raw("POST", "users", body, null).statusCode());
        assertEquals(403, admin.raw("POST", "users", body, "invalid").statusCode());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class));
        String token = admin.csrf();
        Session anotherSession = admin();
        anotherSession.csrf();
        assertEquals(403, anotherSession.raw("POST", "users", body, token).statusCode());
        admin.call("POST", "users", body, 201);
        assertEquals(403, new Session(null, null).raw("POST", "auth/register", accountBody(username(), "USER"), null).statusCode());
    }

    @Test
    void corsDoesNotAllowAnUntrustedWebsite() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/surveys"))
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void authenticationIsRequiredForEveryRequestAndPasswordHashCannotBeInjected() throws Exception {
        String name = username();
        Session anonymous = new Session(null, null);
        String body = accountBody(name, "ADMIN").replace("}", ",\"passwordHash\":\"injected\"}");
        long id = anonymous.call("POST", "auth/register", body, 201).path("id").asLong();
        Session user = new Session(name, PASSWORD);
        user.call("GET", "auth/me", null, 200);
        // Даже cookie с CSRF-сессией не заменяет Basic Auth.
        var noAuth = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me")).GET().build();
        assertEquals(401, user.client.send(noAuth, HttpResponse.BodyHandlers.ofString()).statusCode());
        user.call("PATCH", "users/" + id, "{\"passwordHash\":\"injected\"}", 200);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id = ?", String.class, id);
        assertTrue(encoder.matches(PASSWORD, hash));
        user.call("GET", "auth/me", null, 200);
        anonymous.call("POST", "auth/register", "{\"username\":\"" + username() + "\",\"password\":\"Aa1!" + "я".repeat(40) + "\"}", 400);
        user.call("POST", "surveys", "{broken-json", 400);
    }

    @Test
    void authorsOnlyManageTheirOwnSurveysAndPrivateQuestions() throws Exception {
        String firstName = username(), secondName = username();
        long firstId = account(firstName, "USER").path("id").asLong();
        long secondId = account(secondName, "USER").path("id").asLong();
        Session first = new Session(firstName, PASSWORD), second = new Session(secondName, PASSWORD);
        long surveyId = survey(first, firstId);
        long questionId = question(first, surveyId);
        second.call("GET", "surveys/" + surveyId, null, 403);
        second.call("PATCH", "surveys/" + surveyId, "{\"title\":\"Stolen\"}", 403);
        second.call("DELETE", "surveys/" + surveyId, null, 403);
        second.call("POST", "surveys/" + surveyId + "/publish", null, 403);
        second.call("GET", "survey-questions/" + questionId, null, 403);
        second.call("PATCH", "survey-questions/" + questionId, "{\"text\":\"Stolen\"}", 403);
        second.call("DELETE", "survey-questions/" + questionId, null, 403);
        first.call("PATCH", "surveys/" + surveyId, "{\"authorId\":" + secondId + "}", 403);
        first.call("POST", "surveys", "{\"title\":\"Forged\",\"authorId\":" + secondId + "}", 403);
        for (JsonNode item : second.call("GET", "surveys", null, 200)) assertNotEquals(surveyId, item.path("id").asLong());
        first.call("POST", "surveys/" + surveyId + "/publish", null, 200);
        second.call("GET", "surveys/" + surveyId, null, 200);
        second.call("GET", "survey-questions/" + questionId, null, 200);
        // Permission is valid, but business state still forbids editing a published question.
        first.call("PATCH", "survey-questions/" + questionId, "{\"text\":\"Late edit\"}", 400);
        admin().call("GET", "surveys/" + surveyId, null, 200);
    }

    @Test
    void responsesRemainPrivateAndBusinessActionsRespectOwnershipAndState() throws Exception {
        String authorName = username(), userName = username(), otherName = username();
        long authorId = account(authorName, "USER").path("id").asLong();
        long userId = account(userName, "USER").path("id").asLong();
        long otherId = account(otherName, "USER").path("id").asLong();
        Session author = new Session(authorName, PASSWORD), user = new Session(userName, PASSWORD), other = new Session(otherName, PASSWORD);
        long surveyId = survey(author, authorId), questionId = question(author, surveyId);
        user.call("POST", "surveys", "{\"title\":\"Own survey\",\"authorId\":" + userId + "}", 201);
        author.call("POST", "surveys/" + surveyId + "/publish", null, 200);
        String participationBody = "{\"surveyId\":" + surveyId + ",\"respondentId\":" + userId + "}";
        other.call("POST", "survey-participations", participationBody, 403);
        long participationId = user.call("POST", "survey-participations", participationBody, 201).path("id").asLong();
        for (String method : List.of("GET", "PATCH", "DELETE")) other.call(method, "survey-participations/" + participationId,
                method.equals("PATCH") ? "{}" : null, 403);
        user.call("PATCH", "survey-participations/" + participationId, "{\"respondentId\":" + otherId + "}", 403);
        String answerBody = "{\"participationId\":" + participationId + ",\"questionId\":" + questionId + ",\"selectedValue\":\"Yes\"}";
        other.call("POST", "survey-answers", answerBody, 403);
        author.call("POST", "survey-participations/" + participationId + "/submit", null, 403);
        user.call("POST", "survey-participations/" + participationId + "/submit", null, 400);
        long answerId = user.call("POST", "survey-answers", answerBody, 201).path("id").asLong();
        for (String method : List.of("GET", "PATCH", "DELETE")) other.call(method, "survey-answers/" + answerId,
                method.equals("PATCH") ? "{}" : null, 403);
        for (JsonNode item : other.call("GET", "survey-answers", null, 200)) assertNotEquals(answerId, item.path("id").asLong());
        author.call("GET", "survey-answers/" + answerId, null, 200);
        user.call("POST", "survey-participations/" + participationId + "/submit", null, 200);
        other.call("POST", "survey-participations/" + participationId + "/restart", null, 403);
        user.call("POST", "survey-participations/" + participationId + "/restart", null, 200);
        user.call("GET", "survey-answers/" + answerId, null, 403);
        user.call("POST", "survey-answers", answerBody, 201);
        user.call("POST", "survey-participations/" + participationId + "/submit", null, 200);
        user.call("POST", "surveys/" + surveyId + "/close", null, 403);
        author.call("POST", "surveys/" + surveyId + "/close", null, 200);
        user.call("POST", "survey-participations/" + participationId + "/restart", null, 400);
        user.call("POST", "surveys/" + surveyId + "/copy", null, 403);
        assertEquals("DRAFT", author.call("POST", "surveys/" + surveyId + "/copy", null, 201).path("status").asText());
    }

    @Test
    void userCanChangeOwnPasswordButCannotReadAnotherAccount() throws Exception {
        String name = username(), otherName = username();
        long id = account(name, "USER").path("id").asLong();
        long otherId = account(otherName, "USER").path("id").asLong();
        Session user = new Session(name, PASSWORD);
        user.call("GET", "users/" + otherId, null, 403);
        user.call("DELETE", "users/" + id, null, 403);
        user.call("PATCH", "users/" + id, "{\"email\":\"changed@example.com\",\"password\":\"NewPassword456!\"}", 200);
        assertEquals(401, user.raw("GET", "auth/me", null, null).statusCode());
        new Session(name, "NewPassword456!").call("GET", "auth/me", null, 200);
    }
}
