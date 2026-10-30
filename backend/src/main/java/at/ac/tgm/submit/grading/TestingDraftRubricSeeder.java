package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.PUBLISHED;
import static at.ac.tgm.submit.grading.GradingKeys.PUBLISHED_AT;
import static at.ac.tgm.submit.grading.GradingKeys.PUBLISHED_BY;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC;
import static at.ac.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the immutable, draft-derived rubric snapshot only in the testing profile. */
@Component
@Profile("testing")
@Order(2)
public class TestingDraftRubricSeeder implements ApplicationRunner {
    private static final String FIXTURE = "testing/grading-rubric-draft.json";
    private static final String SOURCE_VERSION = "dev55-draft-testing-v1";
    private static final String SNAPSHOT_PUBLISHED_AT = "2026-10-27T00:00:00Z";

    private final RecordStore store;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public TestingDraftRubricSeeder(RecordStore store, AuditService audit, ObjectMapper objectMapper) {
        this.store = store;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ObjectNode expected = fixture();
        StoredRecord existing = store.page(RUBRIC, null, null,
                        org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(record -> SOURCE_VERSION.equals(store.parse(record).path(VERSION).asText()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (!store.encode(store.parse(existing)).equals(store.encode(expected))) {
                throw new IllegalStateException("Testing grading rubric version '" + SOURCE_VERSION
                        + "' conflicts with an existing immutable version; it was not overwritten.");
            }
            return;
        }

        StoredRecord published = store.create(RUBRIC, null, null, PUBLISHED, expected);
        audit.recordSystem("RUBRIC_PUBLISHED", RUBRIC, published.getId());
    }

    private ObjectNode fixture() {
        try (InputStream input = new ClassPathResource(FIXTURE).getInputStream()) {
            ObjectNode request = objectMapper.readValue(input, ObjectNode.class);
            ObjectNode rubric = RubricValidator.validate(request);
            rubric.putNull(PUBLISHED_BY);
            rubric.put(PUBLISHED_AT, SNAPSHOT_PUBLISHED_AT);
            if (!SOURCE_VERSION.equals(rubric.path(VERSION).asText())) {
                throw new IllegalStateException("Testing rubric fixture version does not match its seeder version.");
            }
            return rubric;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load testing grading rubric fixture " + FIXTURE + ".", exception);
        }
    }
}
