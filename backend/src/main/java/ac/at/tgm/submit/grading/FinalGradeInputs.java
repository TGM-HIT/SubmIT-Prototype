package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.ACTIVE;
import static ac.at.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static ac.at.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.GROUP_GRADES;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC;
import static ac.at.tgm.submit.grading.GradingKeys.METRICS;
import static ac.at.tgm.submit.grading.GradingKeys.METRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_GRADES;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
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

    ObjectNode collect(UUID projectId, String studentId, UUID rubricId) {
        String project = projectId.toString();
        ObjectNode inputs = store.object();
        inputs.set(STUDENT_GRADES, studentId == null ? store.object().arrayNode()
                : latest(STUDENT_GRADE, studentId, project, rubricId, CRITERION_ID));
        inputs.set(GROUP_GRADES, latest(GROUP_GRADE, null, project, rubricId, CRITERION_ID));
        inputs.set(METRICS, latest(METRIC, null, project, rubricId, METRIC_ID));
        return inputs;
    }

    private ArrayNode latest(String type, String owner, String project, UUID rubricId, String keyField) {
        List<StoredRecord> records = store.page(type, owner, project, Pageable.unpaged()).getContent().stream()
                .filter(record -> ACTIVE.equals(record.getStatus()))
                .sorted(Comparator.comparing(StoredRecord::getCreatedAt)).toList();
        Map<String, ObjectNode> byKey = new LinkedHashMap<>();
        for (StoredRecord record : records) {
            ObjectNode view = GradingSupport.view(store, record);
            if (rubricId.toString().equals(view.path(RUBRIC_ID).asText())) {
                byKey.put(view.path(keyField).asText(), view);
            }
        }
        ArrayNode result = store.object().arrayNode();
        byKey.values().forEach(result::add);
        return result;
    }
}
