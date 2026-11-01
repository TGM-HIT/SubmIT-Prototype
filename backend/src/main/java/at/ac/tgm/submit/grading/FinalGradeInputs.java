package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ACTIVE;
import static at.ac.tgm.submit.grading.GradingKeys.ASSESSMENT_TYPE;
import static at.ac.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADES;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC;
import static at.ac.tgm.submit.grading.GradingKeys.METRICS;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADES;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Collects the assessments and metric observations considered for a final grade: the latest current entry per
 * criterion and metric under the bound rubric. Project-level grades see group grades and metrics only; individual
 * grades additionally see that student's own assessments.
 */
@Component
class FinalGradeInputs {
    private final RecordStore store;

    FinalGradeInputs(RecordStore store) {
        this.store = store;
    }

    ObjectNode collect(UUID projectId, String studentId, UUID finalRubricId, UUID routineRubricId) {
        String project = projectId.toString();
        ObjectNode inputs = store.object();
        inputs.set(STUDENT_GRADES, studentId == null ? store.object().arrayNode()
                : latest(new GradeLookup(STUDENT_GRADE, studentId, project, finalRubricId, CRITERION_ID,
                        AssessmentType.FINAL)));
        inputs.set(GROUP_GRADES,
                latest(new GradeLookup(GROUP_GRADE, null, project, routineRubricId, CRITERION_ID, null)));
        inputs.set(METRICS, latest(new GradeLookup(METRIC, null, project, routineRubricId, METRIC_ID, null)));
        return inputs;
    }

    private ArrayNode latest(GradeLookup lookup) {
        List<StoredRecord> records = store.page(lookup.type(), lookup.owner(), lookup.project(), Pageable.unpaged())
                .getContent().stream()
                .filter(record -> ACTIVE.equals(record.getStatus()))
                .sorted(Comparator.comparing(StoredRecord::getCreatedAt).thenComparing(StoredRecord::getId)).toList();
        Map<String, ObjectNode> byKey = new LinkedHashMap<>();
        for (StoredRecord record : records) {
            ObjectNode view = GradingSupport.view(store, record);
            if (lookup.rubricId() != null
                    && lookup.rubricId().toString().equals(view.path(RUBRIC_ID).asText())
                    && (lookup.phase() == null
                    || lookup.phase().name().equals(view.path(ASSESSMENT_TYPE).asText()))) {
                byKey.put(view.path(lookup.keyField()).asText(), view);
            }
        }
        ArrayNode result = store.object().arrayNode();
        byKey.values().forEach(result::add);
        return result;
    }

    private record GradeLookup(String type, String owner, String project, UUID rubricId, String keyField,
            AssessmentType phase) {
    }
}
