package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.example.service.SurveyService;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.CookieManager;
import java.util.Base64;
import java.util.UUID;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/survey_lab4_test}"
})
class ApiRegressionTests {
    @Value("${local.server.port}")
    private int port;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private SurveyService surveyService;
    @Autowired private PlatformTransactionManager transactions;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).cookieHandler(new CookieManager()).build();
    private final JsonMapper json = JsonMapper.builder().build();

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        String authorization = "Basic " + Base64.getEncoder().encodeToString("admin:TestAdmin123!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/" + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .header("Authorization", authorization);
        if (!List.of("GET", "HEAD", "OPTIONS").contains(method)) {
            HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/csrf"))
                    .header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode token = json.readTree(csrf.body());
            builder.header(token.path("headerName").asText(), token.path("token").asText());
        }
        HttpRequest request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode call(String method, String path, String body, int expectedStatus) throws Exception {
        HttpResponse<String> response = request(method, path, body);
        assertEquals(expectedStatus, response.statusCode(), method + " " + path + ": " + response.body());
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    private long create(String resource, String body) throws Exception {
        JsonNode result = call("POST", resource, body, 201);
        assertTrue(result.path("id").isIntegralNumber());
        return result.path("id").asLong();
    }

    private long user() throws Exception {
        return create("users", "{\"username\":\"test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                + "\",\"password\":\"TestAccount123!\",\"email\":\"test@example.com\"}");
    }

    private long survey(long authorId) throws Exception {
        return create("surveys", "{\"title\":\"Survey\",\"authorId\":" + authorId
                + ",\"endDate\":\"" + LocalDateTime.now().plusDays(1) + "\"}");
    }

    private long question(long surveyId) throws Exception {
        return create("survey-questions", "{\"surveyId\":" + surveyId
                + ",\"text\":\"Question\",\"required\":true,\"options\":[\"Yes\",\"No\"]}");
    }

    private long participation(long surveyId, long respondentId) throws Exception {
        return create("survey-participations", "{\"surveyId\":" + surveyId
                + ",\"respondentId\":" + respondentId + "}");
    }

    private String answerBody(long participationId, long questionId) {
        return "{\"participationId\":" + participationId + ",\"questionId\":" + questionId
                + ",\"selectedValue\":\"Yes\"}";
    }

    private long[] activeSurvey() throws Exception {
        long author = user();
        long respondent = user();
        long surveyId = survey(author);
        long questionId = question(surveyId);
        call("POST", "surveys/" + surveyId + "/publish", null, 200);
        long participationId = participation(surveyId, respondent);
        return new long[]{author, respondent, surveyId, questionId, participationId};
    }

    @Test
    void crudUsesDatabaseAndExpectedStatuses() throws Exception {
        long author = user();
        long respondent = user();
        long surveyId = survey(author);
        long questionId = question(surveyId);
        long disposableQuestion = question(surveyId);
        call("PATCH", "users/" + author, "{\"username\":\"updated_" + author + "\"}", 200);
        call("PATCH", "surveys/" + surveyId, "{\"title\":\"Updated\"}", 200);
        JsonNode updated = call("PATCH", "survey-questions/" + questionId, "{\"text\":\"Updated\"}", 200);
        assertTrue(updated.path("required").asBoolean());
        assertEquals(json.readTree("[\"Yes\",\"No\"]"), updated.path("options"));
        call("DELETE", "survey-questions/" + disposableQuestion, null, 204);
        call("POST", "surveys/" + surveyId + "/publish", null, 200);
        long part = participation(surveyId, respondent);
        call("PATCH", "survey-participations/" + part, "{}", 200);
        long answer = create("survey-answers", answerBody(part, questionId));
        call("PATCH", "survey-answers/" + answer, "{\"selectedValue\":\"No\"}", 200);
        String[] resources = {"users", "surveys", "survey-questions", "survey-participations", "survey-answers"};
        long[] ids = {author, surveyId, questionId, part, answer};
        for (int i = 0; i < resources.length; i++) {
            assertTrue(call("GET", resources[i], null, 200).isArray());
            assertEquals(ids[i], call("GET", resources[i] + "/" + ids[i], null, 200).path("id").asLong());
        }
        assertEquals("No", jdbc.queryForObject("SELECT selected_value FROM survey_answers WHERE id = ?", String.class, answer));
        call("DELETE", "survey-answers/" + answer, null, 204);
        call("DELETE", "survey-participations/" + part, null, 204);
        call("DELETE", "surveys/" + surveyId, null, 204);
        call("DELETE", "users/" + author, null, 204);
        call("GET", "survey-questions/" + questionId, null, 404);
    }

    @Test
    void publishChecksDraftQuestionsAndDatesAndCannotBeBypassedByPatch() throws Exception {
        long author = user();
        long surveyId = survey(author);
        call("POST", "surveys/" + surveyId + "/publish", null, 400);
        question(surveyId);
        call("PATCH", "surveys/" + surveyId, "{\"status\":\"ACTIVE\"}", 400);
        assertEquals("ACTIVE", call("POST", "surveys/" + surveyId + "/publish", null, 200).path("status").asText());
        call("POST", "surveys/" + surveyId + "/publish", null, 400);
        call("POST", "survey-questions", "{\"surveyId\":" + surveyId + ",\"text\":\"Late\"}", 400);
        call("POST", "surveys", "{\"title\":\"Invalid\",\"authorId\":" + author + ",\"status\":\"ACTIVE\"}", 400);
        long noDate = create("surveys", "{\"title\":\"No date\",\"authorId\":" + author + "}");
        question(noDate);
        call("POST", "surveys/" + noDate + "/publish", null, 400);
        assertEquals("DRAFT", call("GET", "surveys/" + noDate, null, 200).path("status").asText());
    }

    @Test
    void submitAndRestartRespectRequiredAnswersAndServerTime() throws Exception {
        long[] f = activeSurvey();
        String path = "survey-participations/" + f[4];
        call("POST", path + "/submit", null, 400);
        call("PATCH", path, "{\"status\":\"SUBMITTED\"}", 400);
        long answer = create("survey-answers", answerBody(f[4], f[3]));
        JsonNode submitted = call("POST", path + "/submit", null, 200);
        assertEquals("SUBMITTED", submitted.path("status").asText());
        assertFalse(submitted.path("submittedAt").isNull());
        call("POST", path + "/submit", null, 400);
        call("PATCH", "survey-answers/" + answer, "{\"selectedValue\":\"No\"}", 400);
        JsonNode restarted = call("POST", path + "/restart", null, 200);
        assertEquals("IN_PROGRESS", restarted.path("status").asText());
        assertTrue(restarted.path("submittedAt").isNull());
        call("GET", "survey-answers/" + answer, null, 404);
        call("POST", path + "/restart", null, 400);
        call("POST", path + "/submit", null, 400);
        create("survey-answers", answerBody(f[4], f[3]));
        call("POST", path + "/submit", null, 200);
    }

    @Test
    void closeCancelsUnfinishedParticipationsAndCopyStartsFreshRound() throws Exception {
        long[] f = activeSurvey();
        long answer = create("survey-answers", answerBody(f[4], f[3]));
        call("POST", "survey-participations/" + f[4] + "/submit", null, 200);
        long unfinished = participation(f[2], user());
        call("POST", "surveys/" + f[2] + "/copy", null, 400);
        assertEquals("CLOSED", call("POST", "surveys/" + f[2] + "/close", null, 200).path("status").asText());
        assertEquals("CANCELED", call("GET", "survey-participations/" + unfinished, null, 200).path("status").asText());
        assertEquals("SUBMITTED", call("GET", "survey-participations/" + f[4], null, 200).path("status").asText());
        call("GET", "survey-answers/" + answer, null, 200);
        call("POST", "survey-participations/" + f[4] + "/restart", null, 400);
        call("POST", "surveys/" + f[2] + "/close", null, 400);
        JsonNode copy = call("POST", "surveys/" + f[2] + "/copy", null, 201);
        long copiedId = copy.path("id").asLong();
        assertEquals("DRAFT", copy.path("status").asText());
        assertTrue(copy.path("startDate").isNull());
        assertTrue(copy.path("endDate").isNull());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM survey_questions WHERE survey_id = ?", Integer.class, copiedId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_options o JOIN survey_questions q ON o.question_id=q.id WHERE q.survey_id = ?", Integer.class, copiedId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM survey_participations WHERE survey_id = ?", Integer.class, copiedId));
    }

    @Test
    void rollbackUndoesBothSurveyAndCopiedQuestions() throws Exception {
        long[] f = activeSurvey();
        call("POST", "surveys/" + f[2] + "/close", null, 200);
        int surveysBefore = jdbc.queryForObject("SELECT COUNT(*) FROM surveys", Integer.class);
        int questionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM survey_questions", Integer.class);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            surveyService.copy(f[2]);
            throw new IllegalStateException("Simulated failure after multi-table operation");
        }));
        assertEquals(surveysBefore, jdbc.queryForObject("SELECT COUNT(*) FROM surveys", Integer.class));
        assertEquals(questionsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM survey_questions", Integer.class));
    }

    @Test
    void constraintsPreventDuplicatesCrossSurveyAnswersAndInvalidReferences() throws Exception {
        long[] f = activeSurvey();
        long[] other = activeSurvey();
        call("POST", "survey-participations", "{\"surveyId\":" + f[2] + ",\"respondentId\":" + f[1] + "}", 409);
        call("POST", "survey-answers", answerBody(f[4], other[3]), 400);
        long answer = create("survey-answers", answerBody(f[4], f[3]));
        call("POST", "survey-answers", answerBody(f[4], f[3]), 409);
        call("PATCH", "survey-answers/" + answer, "{\"questionId\":" + other[3] + "}", 400);
        assertEquals(f[3], call("GET", "survey-answers/" + answer, null, 200).path("questionId").asLong());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO survey_answers (survey_id, participation_id, question_id, selected_value) VALUES (?, ?, ?, ?)",
                f[2], f[4], other[3], "Yes"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO surveys (title, author_id, status, created_at, updated_at) VALUES ('Invalid', -1, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
    }

    @Test
    void timestampsFromClientAreIgnoredAndFutureOrExpiredSurveysRejectResponses() throws Exception {
        long[] f = activeSurvey();
        long anotherUser = user();
        JsonNode p = call("POST", "survey-participations", "{\"surveyId\":" + f[2] + ",\"respondentId\":" + anotherUser
                + ",\"createdAt\":\"2000-01-01T00:00:00\",\"submittedAt\":\"2000-01-01T00:00:00\"}", 201);
        assertTrue(p.path("submittedAt").isNull());
        assertTrue(LocalDateTime.parse(p.path("createdAt").asText()).isAfter(LocalDateTime.of(2000,1,1,0,0)));
        jdbc.update("UPDATE surveys SET start_date = ?, end_date = ? WHERE id = ?", LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), f[2]);
        call("POST", "survey-answers", answerBody(f[4], f[3]), 400);
        call("POST", "survey-participations/" + f[4] + "/submit", null, 400);
        jdbc.update("UPDATE surveys SET start_date = ?, end_date = ? WHERE id = ?", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2), f[2]);
        call("POST", "survey-answers", answerBody(f[4], f[3]), 400);
    }

    @Test
    void deletingUserCascadesRelatedDataWithoutDeletingOtherUsers() throws Exception {
        long[] f = activeSurvey();
        long answer = create("survey-answers", answerBody(f[4], f[3]));
        call("DELETE", "users/" + f[0], null, 204);
        for (String path : List.of("surveys/" + f[2], "survey-questions/" + f[3],
                "survey-participations/" + f[4], "survey-answers/" + answer)) {
            call("GET", path, null, 404);
        }
        call("GET", "users/" + f[1], null, 200);
    }

    @Test
    void apiDatabaseUserCannotChangeSchemaOrGrantRights() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT CURRENT_USER")) {
                assertTrue(result.next());
                assertEquals("survey_lab4_app", result.getString(1));
            }
            assertThrows(SQLException.class, () -> statement.execute("CREATE TABLE forbidden_table (id BIGINT)"));
            assertThrows(SQLException.class, () -> statement.execute("DROP TABLE surveys"));
            assertThrows(SQLException.class, () -> statement.execute("CREATE ROLE forbidden_user"));
        }
    }

    @Test
    void simultaneousCloseAndSubmitKeepParticipationConsistent() throws Exception {
        long[] f = activeSurvey();
        create("survey-answers", answerBody(f[4], f[3]));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.<Callable<Integer>>of(
                    () -> request("POST", "surveys/" + f[2] + "/close", null).statusCode(),
                    () -> request("POST", "survey-participations/" + f[4] + "/submit", null).statusCode()));
            assertEquals(200, results.get(0).get().intValue());
            assertTrue(List.of(200, 400).contains(results.get(1).get()));
            JsonNode participation = call("GET", "survey-participations/" + f[4], null, 200);
            if (results.get(1).get() == 200) {
                assertEquals("SUBMITTED", participation.path("status").asText());
                assertFalse(participation.path("submittedAt").isNull());
            } else {
                assertEquals("CANCELED", participation.path("status").asText());
                assertTrue(participation.path("submittedAt").isNull());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
