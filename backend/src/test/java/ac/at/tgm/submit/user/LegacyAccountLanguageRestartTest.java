package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.at.tgm.submit.SubmITlication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Boots the candidate against a real pre-language H2 schema and the same persistent file after restart.
 */
class LegacyAccountLanguageRestartTest {
    private static final UUID STUDENT_ID = UUID.fromString("a6100000-0000-4000-8000-000000000001");
    private static final UUID CLASS_ID = UUID.fromString("a6100000-0000-4000-8000-000000000002");
    private static final UUID SUBJECT_ID = UUID.fromString("a6100000-0000-4000-8000-000000000003");
    private static final String USERNAME = "legacy-student";
    private static final String PASSWORD = "legacy-password-123";
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @TempDir
    Path temporaryDirectory;

    @Test
    void retainsCurrentRoleSetAndBothLanguageChoicesAcrossBackendRestart() throws Exception {
        String databaseUrl = "jdbc:h2:file:" + temporaryDirectory.resolve("account-language").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE";
        createPreFeatureSchema(databaseUrl);
        String baseUrl;
        String token;
        try (ConfigurableApplicationContext application = start(databaseUrl)) {
            baseUrl = baseUrl(application);
            String login = send(baseUrl, "POST", "/api/auth/login", null,
                    Map.of("username", USERNAME, "password", PASSWORD));
            assertTrue(login.contains("\"language\":\"en\""), login);
            assertTrue(login.contains("\"displayName\":\"Legacy Name\""), login);
            assertTrue(login.contains("\"username\":\"" + USERNAME + "\""), login);
            token = token(login);

            HttpResult german = sendResponse(baseUrl, "PATCH", "/api/users/" + STUDENT_ID + "/language",
                    token, Map.of("language", "de"));
            assertEquals(200, german.status(), german.body());
            assertTrue(german.body().contains("\"language\":\"de\""), german.body());
            assertDatabaseInvariants(databaseUrl, "de");

            String registration = send(baseUrl, "POST", "/api/auth/register", null,
                    Map.of("username", "new-student", "password", "a-new-password-123",
                            "displayName", "New Student"));
            assertTrue(registration.contains("\"language\":\"en\""), registration);
            assertTrue(registration.contains("\"displayName\":\"New Student\""), registration);
        }

        try (ConfigurableApplicationContext restarted = start(databaseUrl)) {
            baseUrl = baseUrl(restarted);
            String login = send(baseUrl, "POST", "/api/auth/login", null,
                    Map.of("username", USERNAME, "password", PASSWORD));
            assertTrue(login.contains("\"language\":\"de\""), login);
            HttpResult english = sendResponse(baseUrl, "PATCH", "/api/users/" + STUDENT_ID + "/language",
                    token, Map.of("language", "en"));
            assertEquals(200, english.status(), english.body());
            assertTrue(english.body().contains("\"language\":\"en\""), english.body());
            assertDatabaseInvariants(databaseUrl, "en");
        }

        try (ConfigurableApplicationContext restartedAgain = start(databaseUrl)) {
            String login = send(baseUrl(restartedAgain), "POST", "/api/auth/login", null,
                    Map.of("username", USERNAME, "password", PASSWORD));
            assertTrue(login.contains("\"language\":\"en\""), login);
            assertDatabaseInvariants(databaseUrl, "en");
        }
    }

    private void createPreFeatureSchema(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE student_classes (
                      id UUID PRIMARY KEY,
                      name VARCHAR(100) NOT NULL UNIQUE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE elective_subjects (
                      id UUID PRIMARY KEY,
                      name VARCHAR(100) NOT NULL UNIQUE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE user_accounts (
                      id UUID PRIMARY KEY,
                      username VARCHAR(80) NOT NULL UNIQUE,
                      display_name VARCHAR(120) NOT NULL,
                      password_hash VARCHAR(255) NOT NULL,
                      student_class_id UUID,
                      CONSTRAINT fk_legacy_class FOREIGN KEY (student_class_id) REFERENCES student_classes(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE user_account_roles (
                      user_id UUID NOT NULL,
                      role VARCHAR(20) NOT NULL,
                      FOREIGN KEY (user_id) REFERENCES user_accounts(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE student_elective_subjects (
                      student_id UUID NOT NULL,
                      subject_id UUID NOT NULL,
                      PRIMARY KEY (student_id, subject_id),
                      FOREIGN KEY (student_id) REFERENCES user_accounts(id),
                      FOREIGN KEY (subject_id) REFERENCES elective_subjects(id)
                    )
                    """);
            statement.execute("INSERT INTO student_classes (id, name) VALUES ('" + CLASS_ID + "', 'Legacy Class')");
            statement.execute("INSERT INTO elective_subjects (id, name) VALUES ('" + SUBJECT_ID + "', 'Legacy Science')");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO user_accounts (id, username, display_name, password_hash, student_class_id)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insert.setObject(1, STUDENT_ID);
                insert.setString(2, USERNAME);
                insert.setString(3, "Legacy Name");
                insert.setString(4, new BCryptPasswordEncoder().encode(PASSWORD));
                insert.setObject(5, CLASS_ID);
                insert.executeUpdate();
            }
            statement.execute("INSERT INTO user_account_roles (user_id, role) VALUES ('" + STUDENT_ID + "', 'STUDENT')");
            statement.execute("INSERT INTO student_elective_subjects (student_id, subject_id) VALUES ('"
                    + STUDENT_ID + "', '" + SUBJECT_ID + "')");
        }
    }

    private ConfigurableApplicationContext start(String databaseUrl) {
        return new SpringApplicationBuilder(SubmITlication.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.datasource.url=" + databaseUrl,
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--spring.sql.init.mode=never",
                "--JWT_SECRET=feature21-test-signing-secret-not-for-production",
                        "--server.port=0",
                        "--logging.level.org.springframework.web.servlet.mvc.method.annotation=TRACE",
                        "--spring.main.banner-mode=off");
    }

    private String baseUrl(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return "http://127.0.0.1:" + port;
    }

    private String send(String baseUrl, String method, String path, String token, Object body) throws Exception {
        HttpResult response = sendResponse(baseUrl, method, path, token, body);
        assertTrue(response.status() >= 200 && response.status() < 300,
                method + " " + path + " returned " + response.status() + ": " + response.body());
        return response.body();
    }

    private HttpResult sendResponse(String baseUrl, String method, String path,
            String token, Object body) throws Exception {
        return RestClient.builder().baseUrl(baseUrl).build().method(HttpMethod.valueOf(method)).uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (token != null) headers.setBearerAuth(token);
                })
                .body(body)
                .exchange((request, response) -> new HttpResult(response.getStatusCode().value(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private record HttpResult(int status, String body) {
    }

    private String token(String response) {
        Matcher matcher = TOKEN.matcher(response);
        assertTrue(matcher.find(), "Login response must include an issued token.");
        return matcher.group(1);
    }

    private void assertDatabaseInvariants(String url, String language) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             PreparedStatement query = connection.prepareStatement("""
                     SELECT u.username, u.display_name, u.password_hash, r.role, u.language, c.name, s.name
                     FROM user_accounts u
                     JOIN user_account_roles r ON r.user_id = u.id
                     LEFT JOIN student_classes c ON c.id = u.student_class_id
                     LEFT JOIN student_elective_subjects us ON us.student_id = u.id
                     LEFT JOIN elective_subjects s ON s.id = us.subject_id
                     WHERE u.id = ?
                     """)) {
            query.setObject(1, STUDENT_ID);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(USERNAME, result.getString(1));
                assertEquals("Legacy Name", result.getString(2));
                assertTrue(new BCryptPasswordEncoder().matches(PASSWORD, result.getString(3)));
                assertEquals("STUDENT", result.getString(4));
                assertEquals(language, result.getString(5));
                assertEquals("Legacy Class", result.getString(6));
                assertEquals("Legacy Science", result.getString(7));
            }
        }
    }
}
