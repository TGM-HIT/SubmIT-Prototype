package at.ac.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ideapdf;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class IdeaPdfApiTest {
    private static final byte[] PDF = ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private at.ac.tgm.submit.user.UserAccountRepository users;
    private String student;
    private String unrelatedStudent;
    private String teacher;
    private String admin;

    @BeforeEach
    void authenticate() throws Exception {
        student = login("student01");
        unrelatedStudent = login("student02");
        teacher = login("teacher01");
        admin = login("admin01");
    }

    @Test
    void replacingFormIdeaWithPdfClearsNarrativeAndPreservesManualMetadataAndOwners() throws Exception {
        Map<String, Object> input = Map.of("title", "Updated title", "description", "Old description",
                "intendedOutcome", "Old outcome", "scopePoints", List.of("Old scope"),
                "learningTopics", List.of("Old topic"), "schoolYear", "2026/2027");
        MvcResult created = mockMvc.perform(post("/api/ideas").header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(input)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode before = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        String id = before.path("id").asText();
        String collaboratorId = users.findByUsername("student02").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/ideas/{id}/owners", id).header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("studentId", collaboratorId))))
                .andExpect(status().isOk());
        JsonNode owned = objectMapper.readTree(mockMvc.perform(get("/api/ideas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(2, owned.path("owners").size());
        mockMvc.perform(pdfUpdateRequest(id, student)).andExpect(status().isOk());
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/ideas/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(detail.path("pdfPresent").isBoolean());
        assertTrue(detail.path("pdfPresent").booleanValue());
        assertEquals(owned.path("owners"), detail.path("owners"));
        assertEquals(owned.path("title"), detail.path("title"));
        assertEquals(owned.path("schoolYear"), detail.path("schoolYear"));
        assertEquals(owned.path("creatorId"), detail.path("creatorId"));
        assertEmptyIdeaNarrative(detail);
        JsonNode persisted = store.parse(store.get("idea", java.util.UUID.fromString(id)));
        assertEmptyIdeaNarrative(persisted);
        assertTrue(persisted.path("privatePdfBase64").isTextual());
        JsonNode rows = objectMapper.readTree(mockMvc.perform(get("/api/ideas?size=100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("content");
        JsonNode listed = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> id.equals(row.path("id").asText())).findFirst().orElseThrow();
        assertEmptyIdeaNarrative(listed);
        mockMvc.perform(get("/api/ideas/{id}/pdf", id).header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
    }

    private void assertEmptyIdeaNarrative(JsonNode node) {
        for (String field : List.of("description", "intendedOutcome")) {
            assertTrue(node.path(field).isTextual(), field);
            assertEquals("", node.path(field).textValue());
        }
        for (String field : List.of("scopePoints", "learningTopics")) {
            assertTrue(node.path(field).isArray(), field);
            assertTrue(node.path(field).isEmpty(), field);
        }
    }

    @Test
    void ideaPdfCreateReadAuthorizationAndBoundarySize() throws Exception {
        String ideaId = createPdfIdea("PDF narrative").path("id").asText();
        byte[] replacement = "%PDF-1.6\nupdated private narrative\n".getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(pdfUpdateRequest(ideaId, student, replacement))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.privatePdfBase64").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist());
        mockMvc.perform(get("/api/ideas/{id}", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfPresent").value(true))
                .andExpect(jsonPath("$.privatePdfBase64").doesNotExist());
        mockMvc.perform(get("/api/ideas/{id}/pdf", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(replacement));
        mockMvc.perform(get("/api/ideas/{id}/pdf", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk()).andExpect(content().bytes(replacement));
        mockMvc.perform(get("/api/ideas/{id}/pdf", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ideas/{id}/pdf", ideaId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + ideaId + "')].privatePdfBase64").doesNotExist());
        MvcResult listed = mockMvc.perform(get("/api/ideas?size=100")
                .header(HttpHeaders.AUTHORIZATION, bearer(student))).andExpect(status().isOk()).andReturn();
        JsonNode rows = objectMapper.readTree(listed.getResponse().getContentAsByteArray()).path("content");
        for (JsonNode row : rows) {
            if (ideaId.equals(row.path("id").asText())) {
                assertFalse(row.has("pdfPresent"), "idea lists omit private document availability");
                assertFalse(row.has("privatePdfBase64"));
            }
        }

        byte[] boundary = new byte[10 * 1024 * 1024];
        System.arraycopy(PDF, 0, boundary, 0, PDF.length);
        JsonNode response = createPdfIdea("Maximum supported PDF", boundary);
        assertTrue(response.path("pdfPresent").asBoolean());
        assertArrayEquals(boundary, mockMvc.perform(get("/api/ideas/{id}/pdf", response.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(student))).andReturn().getResponse().getContentAsByteArray());
        String specificationIdeaId = approvedIdea("Maximum specification PDF");
        mockMvc.perform(specificationPdfRequest(specificationIdeaId, pdf(boundary)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pdfPresent").value(true));
        assertArrayEquals(boundary, mockMvc.perform(get("/api/ideas/{id}/specification/pdf", specificationIdeaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(student))).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void invalidPdfMetadataIsRejectedWithoutAnySideEffectsOnBothWriteRoutes() throws Exception {
        String ideaId = createPdfIdea("Metadata no-side-effect baseline").path("id").asText();
        addCollaborator(ideaId);
        JsonNode initialDetail = ideaDetail(ideaId);
        List<String> initialRecords = pdfMetadataStateSnapshot();
        assertArrayEquals(PDF, readIdeaPdf(ideaId));

        for (String field : List.of("title", "schoolYear")) {
            for (String invalidCase : List.of("omitted", "null", "empty", "whitespace", "overlong")) {
                ObjectNode metadata = metadataForInvalidCase(field, invalidCase);

                List<String> beforeCreate = pdfMetadataStateSnapshot();
                assertInvalidMetadataResponse(mockMvc.perform(pdfCreateRequest(metadata, PDF, student)).andReturn());
                assertEquals(beforeCreate, pdfMetadataStateSnapshot(),
                        "Rejected create must not add idea or audit records: " + field + " " + invalidCase);

                assertEquals(initialRecords, pdfMetadataStateSnapshot());
                assertInvalidMetadataResponse(
                        mockMvc.perform(pdfUpdateRequest(ideaId, student, metadata, PDF)).andReturn());
                assertEquals(initialRecords, pdfMetadataStateSnapshot(),
                        "Rejected replacement must preserve idea, PDF, owners and audit history: "
                                + field + " " + invalidCase);
                assertEquals(initialDetail, ideaDetail(ideaId),
                        "Rejected replacement must preserve the authorized idea detail.");
                assertArrayEquals(PDF, readIdeaPdf(ideaId),
                        "Rejected replacement must preserve the existing PDF bytes.");
            }
        }
    }

    @Test
    void pdfMetadataValidBoundariesAndTrimmingWorkOnBothWriteRoutes() throws Exception {
        List<PdfMetadataBoundary> boundaries = List.of(
                new PdfMetadataBoundary("title minimum", "x", "2026/2027", "x", "2026/2027"),
                new PdfMetadataBoundary("title maximum", "T".repeat(200), "2026/2027",
                        "T".repeat(200), "2026/2027"),
                new PdfMetadataBoundary("title padded", "  title value  ", "2026/2027",
                        "title value", "2026/2027"),
                new PdfMetadataBoundary("school year minimum", "Fixed title", "x", "Fixed title", "x"),
                new PdfMetadataBoundary("school year maximum", "Fixed title", "Y".repeat(20),
                        "Fixed title", "Y".repeat(20)),
                new PdfMetadataBoundary("school year padded", "Fixed title", "  2026/2027  ",
                        "Fixed title", "2026/2027"));

        for (PdfMetadataBoundary boundary : boundaries) {
            JsonNode created = readResponse(mockMvc.perform(pdfCreateRequest(
                    metadata(boundary.title(), boundary.schoolYear()), PDF, student))
                    .andExpect(status().isCreated()).andReturn());
            assertMetadataAndPdfReadback(created, boundary, PDF);
            assertEquals(1, created.path("owners").size(),
                    "Omitted collaboratorIds remains accepted and creation retains only its creator.");
        }

        String ideaId = createPdfIdea("Metadata replacement baseline").path("id").asText();
        addCollaborator(ideaId);
        JsonNode ownersBefore = ideaDetail(ideaId).path("owners").deepCopy();
        for (PdfMetadataBoundary boundary : boundaries) {
            JsonNode updated = readResponse(mockMvc.perform(pdfUpdateRequest(ideaId, student,
                    metadata(boundary.title(), boundary.schoolYear()), PDF))
                    .andExpect(status().isOk()).andReturn());
            assertMetadataAndPdfReadback(updated, boundary, PDF);
            assertEquals(ownersBefore, ideaDetail(ideaId).path("owners"),
                    "Metadata replacement without collaboratorIds preserves the existing owner set.");
        }
    }

    @Test
    void teacherAuthorsCanUsePdfAndOnlyAcceptedCatalogueReadersCanReadUnownedPdf() throws Exception {
        JsonNode teacherIdea = createPdfIdea("Teacher-authored PDF", PDF, teacher);
        String teacherIdeaId = teacherIdea.path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", teacherIdeaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk());
        mockMvc.perform(pdfUpdateRequest(teacherIdeaId, teacher))
                .andExpect(status().isConflict());
        String secondTeacher = login("teacher02");
        JsonNode teacherSpecificationIdea = createPdfIdea("Teacher-authored specification PDF", PDF, teacher);
        String teacherSpecificationIdeaId = teacherSpecificationIdea.path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", teacherSpecificationIdeaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/reviews", teacherSpecificationIdeaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondTeacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("decision", "APPROVE_FOR_SPECIFICATION",
                                "feedback", "Proceed."))))
                .andExpect(status().isCreated());
        mockMvc.perform(specificationPdfRequest(teacherSpecificationIdeaId, pdf(PDF), teacher))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pdfPresent").value(true));
        mockMvc.perform(get("/api/ideas/{id}/specification/pdf", teacherSpecificationIdeaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));

        JsonNode target = createPdfIdea("Accepted catalogue PDF", PDF);
        String targetId = target.path("id").asText();
        acceptIdea(targetId, true);
        JsonNode second = createIdea("Accepted catalogue project 2");
        String secondId = second.path("id").asText();
        acceptIdea(secondId, false);
        JsonNode third = createIdea("Accepted catalogue project 3");
        String thirdId = third.path("id").asText();
        acceptIdea(thirdId, false);
        JsonNode outside = createPdfIdea("Outside the catalogue PDF", PDF);
        String outsideId = outside.path("id").asText();
        acceptIdea(outsideId, true);

        MvcResult period = mockMvc.perform(post("/api/application-periods")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("label", "PDF catalogue test",
                                "schoolYear", "2026/2027", "opensAt", Instant.now().minusSeconds(60).toString(),
                                "closesAt", Instant.now().plusSeconds(3600).toString(),
                                "projectIds", List.of(targetId, secondId, thirdId)))))
                .andExpect(status().isCreated()).andReturn();
        String periodId = objectMapper.readTree(period.getResponse().getContentAsByteArray()).path("id").asText();

        mockMvc.perform(get("/api/application-periods/{period}/projects/{idea}/pdf", periodId, targetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mockMvc.perform(get("/api/application-periods/{period}/projects/{idea}/specification/pdf",
                        periodId, targetId).header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mockMvc.perform(get("/api/ideas/{id}/pdf", outsideId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{period}/projects/{idea}/pdf", periodId, outsideId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(unrelatedStudent)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/application-periods/{period}/projects/{idea}/pdf", periodId, targetId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidIdeaAndSpecificationUploadsDoNotCreateOrChangeRecords() throws Exception {
        String ideaId = approvedIdea("Invalid specification uploads");
        List<String> originalRecords = recordSnapshot();
        for (MockMultipartFile invalid : List.of(
                new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
                new MockMultipartFile("file", "wrong.txt", "application/pdf", PDF),
                new MockMultipartFile("file", "invalid.pdf", "application/pdf", "not pdf".getBytes(StandardCharsets.US_ASCII)),
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]))) {
            mockMvc.perform(pdfCreateRequest(invalid)).andExpect(status().isBadRequest());
            assertEquals(originalRecords, recordSnapshot(),
                    "invalid idea PDF must not create or alter idea/revision/audit records");
            mockMvc.perform(specificationPdfRequest(ideaId, invalid)).andExpect(status().isBadRequest());
            assertEquals(originalRecords, recordSnapshot(),
                    "invalid specification PDF must not change idea/revision/audit records");
        }
        mockMvc.perform(pdfCreateRequest(pdf(PDF)).param("description", "conflict"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(specificationPdfRequest(ideaId, pdf(PDF)).param("objectives", "conflict"))
                .andExpect(status().isBadRequest());
        assertEquals(originalRecords, recordSnapshot());
    }

    @Test
    void specificationRevisionRetainsReviewedPdfBytesAndSubmittedContentIsImmutable() throws Exception {
        String ideaId = approvedIdea("Specification PDF revisions");
        mockMvc.perform(specificationPdfRequest(ideaId, pdf(PDF)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pdfPresent").value(true));
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        mockMvc.perform(specificationPdfRequest(ideaId, pdf("%PDF-1.7\nchanged\n".getBytes(StandardCharsets.US_ASCII))))
                .andExpect(status().isConflict());
        MvcResult reviewed = mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("decision", "REQUEST_REVISION",
                                "feedback", "Please revise.", "workSize", 2, "curriculumRelevance", 2,
                                "technicalComplexity", 2, "feasibility", 2, "learningValue", 2,
                                "resourceNeeds", 2, "riskAndDependencies", 2))))
                .andExpect(status().isCreated()).andReturn();
        String reviewId = objectMapper.readTree(reviewed.getResponse().getContentAsByteArray()).path("id").asText();
        StoredRecord snapshot = store.repository().findByTypeAndParentId("specification_review_snapshot", reviewId)
                .stream().findFirst().orElseThrow();
        assertArrayEquals(PDF, Base64.getDecoder().decode(store.parse(snapshot).path("privatePdfBase64").asText()));
        byte[] replacement = "%PDF-1.7\nrevision\n".getBytes(StandardCharsets.US_ASCII);
        mockMvc.perform(specificationPdfRequest(ideaId, pdf(replacement))).andExpect(status().isOk());
        assertArrayEquals(replacement, mockMvc.perform(get("/api/ideas/{id}/specification/pdf", ideaId)
                .header(HttpHeaders.AUTHORIZATION, bearer(student))).andReturn().getResponse().getContentAsByteArray());
        assertArrayEquals(PDF, Base64.getDecoder().decode(store.parse(snapshot).path("privatePdfBase64").asText()));
    }

    private JsonNode createPdfIdea(String title) throws Exception {
        return createPdfIdea(title, PDF);
    }

    private JsonNode createPdfIdea(String title, byte[] bytes) throws Exception {
        return createPdfIdea(title, bytes, student);
    }

    private JsonNode createPdfIdea(String title, byte[] bytes, String token) throws Exception {
        MockMultipartFile metadata = new MockMultipartFile("metadata", "metadata.json", "application/json",
                objectMapper.writeValueAsBytes(Map.of("title", title, "schoolYear", "2026/2027")));
        MvcResult result = mockMvc.perform(multipart("/api/ideas/pdf").file(metadata).file(pdf(bytes))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pdfPresent").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void addCollaborator(String ideaId) throws Exception {
        String collaboratorId = users.findByUsername("student02").orElseThrow().getId().toString();
        mockMvc.perform(post("/api/ideas/{id}/owners", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("studentId", collaboratorId))))
                .andExpect(status().isOk());
    }

    private JsonNode ideaDetail(String ideaId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ideas/{id}", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private byte[] readIdeaPdf(String ideaId) throws Exception {
        return mockMvc.perform(get("/api/ideas/{id}/pdf", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void assertMetadataAndPdfReadback(JsonNode response, PdfMetadataBoundary boundary, byte[] pdfBytes)
            throws Exception {
        assertEquals(boundary.expectedTitle(), response.path("title").asText());
        assertEquals(boundary.expectedSchoolYear(), response.path("schoolYear").asText());
        String ideaId = response.path("id").asText();
        JsonNode detail = ideaDetail(ideaId);
        assertEquals(boundary.expectedTitle(), detail.path("title").asText());
        assertEquals(boundary.expectedSchoolYear(), detail.path("schoolYear").asText());
        assertArrayEquals(pdfBytes, readIdeaPdf(ideaId), "PDF readback must retain the uploaded bytes.");
    }

    private void assertInvalidMetadataResponse(MvcResult result) throws Exception {
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(error.path("status").isInt());
        assertEquals(400, error.path("status").intValue());
        assertEquals("Bad Request", error.path("error").asText());
        assertEquals("PDF idea metadata is invalid.", error.path("message").asText());
    }

    private ObjectNode metadata(String title, String schoolYear) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("title", title);
        result.put("schoolYear", schoolYear);
        return result;
    }

    private ObjectNode metadataForInvalidCase(String field, String invalidCase) {
        ObjectNode result = objectMapper.createObjectNode();
        String otherField = "title".equals(field) ? "schoolYear" : "title";
        result.put(otherField, "title".equals(otherField) ? "Valid title" : "2026/2027");
        switch (invalidCase) {
            case "omitted" -> {
                // Keep the tested property absent.
            }
            case "null" -> result.putNull(field);
            case "empty" -> result.put(field, "");
            case "whitespace" -> result.put(field, "   ");
            case "overlong" -> result.put(field, "x".repeat("title".equals(field) ? 201 : 21));
            default -> throw new IllegalArgumentException("Unknown invalid metadata case: " + invalidCase);
        }
        return result;
    }

    private JsonNode createIdea(String title) throws Exception {
        Map<String, Object> input = Map.of("title", title, "description", "A catalogue idea",
                "intendedOutcome", "A useful result", "scopePoints", List.of("A scope point"),
                "learningTopics", List.of(), "schoolYear", "2026/2027");
        MvcResult response = mockMvc.perform(post("/api/ideas").header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(input)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(response.getResponse().getContentAsByteArray());
    }

    private void acceptIdea(String ideaId, boolean specificationPdf) throws Exception {
        mockMvc.perform(post("/api/ideas/{id}/submit", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/reviews", ideaId).header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("decision", "APPROVE_FOR_SPECIFICATION",
                                "feedback", "Proceed."))))
                .andExpect(status().isCreated());
        if (specificationPdf) {
            mockMvc.perform(specificationPdfRequest(ideaId, pdf(PDF))).andExpect(status().isOk());
        } else {
            Map<String, Object> spec = Map.of("objectives", List.of("Objective"),
                    "intendedUsers", List.of("Students"), "requirements", List.of("Requirement"),
                    "deliverables", List.of("Deliverable"), "scope", "Proposed scope",
                    "dependencies", List.of("Resource"));
            mockMvc.perform(put("/api/ideas/{id}/specification", ideaId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(student))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(spec)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        Map<String, Object> decision = Map.of("decision", "ACCEPT_FOR_APPLICATIONS", "feedback", "Accepted.",
                "workSize", 2, "curriculumRelevance", 2, "technicalComplexity", 2, "feasibility", 2,
                "learningValue", 2, "resourceNeeds", 2, "riskAndDependencies", 2);
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", ideaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(decision)))
                .andExpect(status().isCreated());
    }

    private String approvedIdea(String title) throws Exception {
        Map<String, Object> input = Map.of("title", title, "description", "Description",
                "intendedOutcome", "Outcome", "scopePoints", List.of("Scope"), "learningTopics", List.of(),
                "schoolYear", "2026/2027");
        MvcResult created = mockMvc.perform(post("/api/ideas").header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(input)))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsByteArray()).path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", id).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/reviews", id).header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("decision", "APPROVE_FOR_SPECIFICATION",
                                "feedback", "Proceed."))))
                .andExpect(status().isCreated());
        return id;
    }

    private MockMultipartHttpServletRequestBuilder pdfCreateRequest(MockMultipartFile file) throws Exception {
        return multipart("/api/ideas/pdf")
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json",
                        objectMapper.writeValueAsBytes(Map.of("title", "Invalid", "schoolYear", "2026/2027"))))
                .file(file).header(HttpHeaders.AUTHORIZATION, bearer(student));
    }

    private MockMultipartHttpServletRequestBuilder pdfCreateRequest(JsonNode metadata, byte[] bytes, String token)
            throws Exception {
        return multipart("/api/ideas/pdf")
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json",
                        objectMapper.writeValueAsBytes(metadata)))
                .file(pdf(bytes)).header(HttpHeaders.AUTHORIZATION, bearer(token));
    }

    private MockMultipartHttpServletRequestBuilder specificationPdfRequest(String id, MockMultipartFile file) {
        return specificationPdfRequest(id, file, student);
    }

    private MockMultipartHttpServletRequestBuilder specificationPdfRequest(String id, MockMultipartFile file,
            String token) {
        return multipart("/api/ideas/{id}/specification/pdf", id).file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).with(request -> {
                    request.setMethod("PUT");
                    return request;
                });
    }

    private MockMultipartHttpServletRequestBuilder pdfUpdateRequest(String id, String token) throws Exception {
        return pdfUpdateRequest(id, token, PDF);
    }

    private MockMultipartHttpServletRequestBuilder pdfUpdateRequest(String id, String token, byte[] bytes)
            throws Exception {
        return multipart("/api/ideas/{id}/pdf", id)
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json",
                        objectMapper.writeValueAsBytes(Map.of("title", "Updated title", "schoolYear", "2026/2027"))))
                .file(pdf(bytes)).header(HttpHeaders.AUTHORIZATION, bearer(token)).with(request -> {
                    request.setMethod("PUT");
                    return request;
                });
    }

    private MockMultipartHttpServletRequestBuilder pdfUpdateRequest(String id, String token, JsonNode metadata,
            byte[] bytes) throws Exception {
        return multipart("/api/ideas/{id}/pdf", id)
                .file(new MockMultipartFile("metadata", "metadata.json", "application/json",
                        objectMapper.writeValueAsBytes(metadata)))
                .file(pdf(bytes)).header(HttpHeaders.AUTHORIZATION, bearer(token)).with(request -> {
                    request.setMethod("PUT");
                    return request;
                });
    }

    private MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "narrative.pdf", "application/pdf", bytes);
    }

    private List<String> recordSnapshot() {
        return RecordSnapshots.capture(store.repository().findAll());
    }

    private List<String> pdfMetadataStateSnapshot() {
        List<String> snapshot = new java.util.ArrayList<>();
        store.repository().findByType("idea", PageRequest.of(0, 10000)).forEach(record ->
                snapshot.add(String.join("\u001f", record.getId().toString(), String.valueOf(record.getStatus()),
                        String.valueOf(record.getOwnerId()), String.valueOf(record.getParentId()))));
        store.repository().findByType("audit", PageRequest.of(0, 10000)).forEach(record ->
                snapshot.add(String.join("\u001f", record.getId().toString(), String.valueOf(record.getOwnerId()),
                        String.valueOf(record.getParentId()), String.valueOf(record.getReferenceType()),
                        String.valueOf(record.getStatus()), String.valueOf(record.getPayload()),
                        String.valueOf(record.getCreatedAt()))));
        return snapshot.stream().sorted().toList();
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("username", username,
                                "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("token").asText();
    }

    private record PdfMetadataBoundary(String label, String title, String schoolYear,
            String expectedTitle, String expectedSchoolYear) {
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
