package at.ac.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:artifactmultipartlimits;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
class ArtifactMultipartLimitsApiTest {
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 11 * 1024 * 1024;
    private static final int OVER_FILE_BYTES = MAX_FILE_BYTES + 1;
    private static final int AGGREGATE_PART_BYTES = 6 * 1024 * 1024;
    private static final String PASSWORD = "testing-password";
    private static final String BOUNDARY = "Bug38MultipartBoundary";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RecordStore store;

    @Autowired
    private UserAccountRepository users;

    private HttpClient client;
    private String teacherToken;
    private String outsiderToken;
    private UUID projectId;

    @BeforeEach
    void setUp() throws Exception {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JsonNode teacherLogin = jsonRequest("POST", "/api/auth/login", "application/json",
                mapper.writeValueAsBytes(Map.of("username", "teacher01", "password", PASSWORD)), null);
        teacherToken = teacherLogin.path("token").asText();
        JsonNode outsiderLogin = jsonRequest("POST", "/api/auth/login", "application/json",
                mapper.writeValueAsBytes(Map.of("username", "student04", "password", PASSWORD)), null);
        outsiderToken = outsiderLogin.path("token").asText();

        UUID memberId = users.findByUsername("student01").orElseThrow().getId();
        projectId = store.create("project", null, null, "IN_PROGRESS",
                store.object().put("title", "Bug-38 multipart regression fixture")).getId();
        var group = store.object();
        group.putArray("studentIds").add(memberId.toString());
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        store.create("formal_application", memberId.toString(), projectId.toString(), "APPROVED",
                store.object().put("topic", "Bug-38 multipart regression fixture"));
        HttpResponse<byte[]> authenticatedList = get("/api/projects/" + projectId + "/artifacts", teacherToken);
        assertEquals(200, authenticatedList.statusCode(), responseText(authenticatedList));
        HttpResponse<byte[]> deniedList = get("/api/projects/" + projectId + "/artifacts", outsiderToken);
        assertEquals(403, deniedList.statusCode(), responseText(deniedList));
    }

    @Test
    void realServletParserLimitsExpose413WithoutChangingAuthenticationOrAuthorization() throws Exception {
        Map<String, List<String>> beforeDenied = stateSnapshot();
        HttpResponse<byte[]> missingToken = sendUpload(smallMultipart(), null);
        assertErrorResponse(missingToken, 401);
        assertEquals(beforeDenied, stateSnapshot(), "Missing credentials must not write upload state.");

        HttpResponse<byte[]> invalidToken = sendUpload(smallMultipart(), "not-a-valid-token");
        assertErrorResponse(invalidToken, 401);
        assertEquals(beforeDenied, stateSnapshot(), "Invalid credentials must not write upload state.");

        HttpResponse<byte[]> unauthorizedProject = sendUpload(smallMultipart(), outsiderToken);
        assertErrorResponse(unauthorizedProject, 403);
        assertEquals(beforeDenied, stateSnapshot(), "Unauthorized project access must not write upload state.");

        byte[] smallFile = bytes(14, (byte) 0x31);
        HttpResponse<byte[]> smallUpload = sendUpload(multipart(
                List.of(new Part("file", "small.bin", smallFile)), "Small boundary control"), teacherToken);
        assertEquals(201, smallUpload.statusCode(), responseText(smallUpload));
        JsonNode smallResponse = mapper.readTree(smallUpload.body());
        assertEquals("PRIVATE", smallResponse.path("publicationStatus").asText());
        assertArrayEquals(smallFile, retrieve(smallResponse.path("id").asText(), teacherToken));

        Map<String, List<String>> beforeEmptyFile = stateSnapshot();
        HttpResponse<byte[]> emptyUpload = sendUpload(multipart(
                List.of(new Part("file", "empty.bin", new byte[0])), "Empty-file validation control"), teacherToken);
        assertEquals(400, emptyUpload.statusCode(), responseText(emptyUpload));
        assertTrue(emptyUpload.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("")
                .toLowerCase().contains("application/json"));
        JsonNode emptyError = mapper.readTree(emptyUpload.body());
        assertErrorBody(emptyError, 400, "Bad Request");
        assertEquals(beforeEmptyFile, stateSnapshot(), "Empty-file validation must not create upload state.");

        byte[] boundaryFile = bytes(MAX_FILE_BYTES, (byte) 0x52);
        byte[] boundaryBody = multipart(
                List.of(new Part("file", "boundary.bin", boundaryFile)), "Exact file boundary control");
        assertTrue(boundaryBody.length < MAX_REQUEST_BYTES,
                "The exact per-file boundary must stay below the aggregate request ceiling.");
        HttpResponse<byte[]> boundaryUpload = sendUpload(boundaryBody, teacherToken);
        assertEquals(201, boundaryUpload.statusCode(), responseText(boundaryUpload));
        JsonNode boundaryResponse = mapper.readTree(boundaryUpload.body());
        assertEquals("PRIVATE", boundaryResponse.path("publicationStatus").asText());
        assertArrayEquals(boundaryFile, retrieve(boundaryResponse.path("id").asText(), teacherToken));

        Map<String, List<String>> beforePerFileLimit = stateSnapshot();
        byte[] overLimitFile = bytes(OVER_FILE_BYTES, (byte) 0x63);
        byte[] perFileBody = multipart(
                List.of(new Part("file", "over-limit.bin", overLimitFile)), "Per-file parser trigger");
        assertTrue(overLimitFile.length > MAX_FILE_BYTES);
        assertTrue(perFileBody.length < MAX_REQUEST_BYTES,
                "The per-file trigger must not also exceed the aggregate request ceiling.");
        HttpResponse<byte[]> perFileResponse = sendUpload(perFileBody, teacherToken);
        assertEquals(413, perFileResponse.statusCode(), responseText(perFileResponse));
        assertFalse(perFileResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE).isPresent(),
                "The per-file parser error has no Content-Type; document no response content.");
        assertEquals(0, perFileResponse.body().length,
                "The per-file parser error currently has no response body.");
        printParserEvidence("per-file", perFileBody.length, List.of(overLimitFile.length), perFileResponse);
        assertEquals(beforePerFileLimit, stateSnapshot(), "Per-file parser rejection must create no records.");

        Map<String, List<String>> beforeAggregateLimit = stateSnapshot();
        byte[] aggregateFileA = bytes(AGGREGATE_PART_BYTES, (byte) 0x74);
        byte[] aggregateFileB = bytes(AGGREGATE_PART_BYTES, (byte) 0x25);
        assertTrue(aggregateFileA.length <= MAX_FILE_BYTES && aggregateFileB.length <= MAX_FILE_BYTES);
        byte[] aggregateBody = multipart(List.of(
                new Part("file", "aggregate-a.bin", aggregateFileA),
                new Part("extra", "aggregate-b.bin", aggregateFileB)), "Aggregate-only parser trigger");
        assertTrue(aggregateBody.length > MAX_REQUEST_BYTES,
                "The serialized aggregate body must exceed the configured request limit.");
        HttpResponse<byte[]> aggregateResponse = sendUpload(aggregateBody, teacherToken);
        assertEquals(413, aggregateResponse.statusCode(), responseText(aggregateResponse));
        assertFalse(aggregateResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE).isPresent(),
                "The aggregate parser error has no Content-Type; document no response content.");
        assertEquals(0, aggregateResponse.body().length,
                "The aggregate parser error currently has no response body.");
        printParserEvidence("aggregate-only", aggregateBody.length,
                List.of(aggregateFileA.length, aggregateFileB.length), aggregateResponse);
        assertEquals(beforeAggregateLimit, stateSnapshot(), "Aggregate parser rejection must create no records.");

        assertEquals(perFileResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE),
                aggregateResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE));
        assertEquals(mapper.readTree(perFileResponse.body()), mapper.readTree(aggregateResponse.body()),
                "Both parser triggers must expose the same response representation.");
    }

    private HttpResponse<byte[]> sendUpload(byte[] body, String token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/projects/" + projectId + "/artifacts"))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private byte[] retrieve(String artifactId, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/artifacts/" + artifactId + "/content"))
                .header("Accept", "application/octet-stream")
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private JsonNode jsonRequest(String method, String path, String contentType, byte[] body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json")
                .header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), responseText(response));
        return mapper.readTree(response.body());
    }

    private HttpResponse<byte[]> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private byte[] smallMultipart() throws IOException {
        return multipart(List.of(new Part("file", "control.bin", bytes(16, (byte) 0x16))),
                "Authentication and authorization control");
    }

    private byte[] multipart(List<Part> parts, String title) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"type\"\r\n\r\nPOSTER\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n" + title + "\r\n");
        for (Part part : parts) {
            write(body, "--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + part.name()
                    + "\"; filename=\"" + part.filename() + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n");
            body.write(part.bytes());
            write(body, "\r\n");
        }
        write(body, "--" + BOUNDARY + "--\r\n");
        return body.toByteArray();
    }

    private static void write(ByteArrayOutputStream body, String value) throws IOException {
        body.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(int size, byte seed) {
        byte[] result = new byte[size];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) (seed + index % 23);
        }
        return result;
    }

    private void assertErrorResponse(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), responseText(response));
        assertErrorBody(mapper.readTree(response.body()), status, null);
    }

    private void assertErrorBody(JsonNode body, int status, String expectedError) {
        assertTrue(body.isObject(), "Application error body must be an object.");
        JsonNode statusNode = body.get("status");
        assertTrue(statusNode != null && statusNode.isIntegralNumber(), "Application error status must be numeric.");
        assertEquals(status, statusNode.intValue());
        JsonNode errorNode = body.get("error");
        assertTrue(errorNode != null && errorNode.isTextual(), "Application error name must be a string.");
        if (expectedError != null) {
            assertEquals(expectedError, errorNode.textValue());
        }
        JsonNode messageNode = body.get("message");
        assertTrue(messageNode != null && messageNode.isTextual(), "Application error message must be a string.");
    }

    private Map<String, List<String>> stateSnapshot() {
        Map<String, List<String>> snapshot = new java.util.TreeMap<>();
        snapshot.put("project", List.of(recordValue(store.repository().findById(projectId).orElseThrow())));
        snapshot.put("formal_application",
                store.repository().findByTypeAndParentId("formal_application", projectId.toString()).stream()
                        .map(this::recordValue).sorted().toList());
        snapshot.put("artifacts",
                store.repository().findByTypeAndParentId("artifact", projectId.toString()).stream()
                        .map(this::recordValue).sorted().toList());
        var audits = store.page("audit", null, null, org.springframework.data.domain.PageRequest.of(0, 1000));
        assertEquals(audits.getTotalElements(), audits.getNumberOfElements(),
                "No-write verification must include the complete audit inventory.");
        snapshot.put("audit", audits.getContent().stream().map(this::recordValue).sorted().toList());
        return snapshot;
    }

    private String recordValue(StoredRecord record) {
        return record.getId() + "|" + record.getType() + "|" + record.getOwnerId() + "|" + record.getParentId()
                + "|" + record.getReferenceType() + "|" + record.getStatus() + "|" + record.getPayload();
    }

    private static String responseText(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static void printParserEvidence(String trigger, int requestBytes, List<Integer> filePartBytes,
            HttpResponse<byte[]> response) {
        System.out.printf("BUG38 parser evidence scenario=%s requestBytes=%d filePartBytes=%s accept=application/json "
                        + "status=%d responseHeaders=%s responseBodyBytes=%d%n",
                trigger, requestBytes, filePartBytes, response.statusCode(), response.headers().map(),
                response.body().length);
    }

    private record Part(String name, String filename, byte[] bytes) {
    }
}
