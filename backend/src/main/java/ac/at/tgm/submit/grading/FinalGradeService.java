package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.CURRENT;
import static ac.at.tgm.submit.grading.GradingKeys.CURRENT_KEY;
import static ac.at.tgm.submit.grading.GradingKeys.DECIDED_AT;
import static ac.at.tgm.submit.grading.GradingKeys.DECIDED_BY;
import static ac.at.tgm.submit.grading.GradingKeys.FINAL_GRADE;
import static ac.at.tgm.submit.grading.GradingKeys.HISTORY;
import static ac.at.tgm.submit.grading.GradingKeys.INPUTS;
import static ac.at.tgm.submit.grading.GradingKeys.PROJECT;
import static ac.at.tgm.submit.grading.GradingKeys.PROJECT_OWNER;
import static ac.at.tgm.submit.grading.GradingKeys.RULE_ID;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC_VERSION;
import static ac.at.tgm.submit.grading.GradingKeys.SCOPE;
import static ac.at.tgm.submit.grading.GradingKeys.SCOPE_PROJECT;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENTS;
import static ac.at.tgm.submit.grading.GradingKeys.STUDENT_ID;
import static ac.at.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static ac.at.tgm.submit.grading.GradingKeys.SUPERSEDED;
import static ac.at.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradeHistory;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradesRequest;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradesResponse;
import ac.at.tgm.submit.security.AuthenticatedUser;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalGradeService {
    private final RecordStore store;
    private final AuditService audit;
    private final ProjectAccess access;
    private final RubricBinding binding;
    private final FinalGradeInputs inputs;

    public FinalGradeService(RecordStore store, AuditService audit, ProjectAccess access, RubricBinding binding,
            FinalGradeInputs inputs) {
        this.store = store;
        this.audit = audit;
        this.access = access;
        this.binding = binding;
        this.inputs = inputs;
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public FinalGradesResponse record(AuthenticatedUser caller, UUID projectId, FinalGradesRequest body) {
        List<String> members = access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        JsonNode studentEntries = request.path(STUDENTS);
        if (!request.has(PROJECT) && !studentEntries.isArray()) {
            throw GradingSupport.bad("Provide a 'project' entry and/or a 'students' array.");
        }
        GradingSupport.requireDistinctMembers(studentEntries, members);
        RubricRef rubric = binding.bind(caller, projectId, request);
        ObjectNode result = store.object();
        if (request.has(PROJECT)) {
            result.set(PROJECT, GradingSupport.view(store,
                    decide(caller, projectId, rubric, request.path(PROJECT), null)));
        }
        ArrayNode students = result.putArray(STUDENTS);
        for (JsonNode entry : studentEntries) {
            students.add(GradingSupport.view(store,
                    decide(caller, projectId, rubric, entry, entry.path(STUDENT_ID).asText())));
        }
        return GradingSupport.convert(result, FinalGradesResponse.class);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public FinalGradeHistory getProjectFinalGrade(AuthenticatedUser caller, UUID projectId) {
        access.requireTeacherOrMember(caller, projectId);
        return history(projectId, PROJECT_OWNER);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public FinalGradeHistory getStudentFinalGrade(AuthenticatedUser caller, UUID projectId, UUID studentId) {
        List<String> members = access.requireTeacherOrMember(caller, projectId);
        access.requireTeacherOrSelf(caller, members, studentId.toString());
        if (!members.contains(studentId.toString())) {
            throw new EntityNotFoundException("The student is not a member of this project.");
        }
        return history(projectId, studentId.toString());
    }

    private StoredRecord decide(AuthenticatedUser caller, UUID projectId, RubricRef rubric, JsonNode entry,
            String studentId) {
        GradingSupport.requireObject(entry);
        ObjectNode considered = inputs.collect(projectId, studentId, rubric.id());
        ObjectNode payload = store.object();
        payload.put(RUBRIC_ID, rubric.id().toString());
        payload.put(RUBRIC_VERSION, rubric.payload().path(VERSION).asText());
        payload.put(SCOPE, studentId == null ? SCOPE_PROJECT : SUBJECT_STUDENT);
        if (studentId != null) {
            payload.put(STUDENT_ID, studentId);
        }
        String ruleId = GradingSupport.optionalText(entry, RULE_ID);
        if (ruleId == null) {
            FinalGradeMethods.manual(payload, entry, rubric);
        } else {
            FinalGradeMethods.rule(payload, entry, rubric, ruleId, considered);
        }
        payload.set(INPUTS, considered);
        payload.put(DECIDED_BY, caller.id().toString());
        payload.put(DECIDED_AT, GradingSupport.nowText());
        String owner = studentId == null ? PROJECT_OWNER : studentId;
        supersedeCurrent(projectId, owner);
        StoredRecord record = store.create(FINAL_GRADE, owner, projectId.toString(), CURRENT, payload);
        audit.record(caller, "FINAL_GRADE_RECORDED", FINAL_GRADE, record.getId());
        return record;
    }

    private void supersedeCurrent(UUID projectId, String owner) {
        for (StoredRecord previous : GradingSupport.all(store, FINAL_GRADE, owner, projectId.toString())) {
            if (CURRENT.equals(previous.getStatus())) {
                store.update(previous, SUPERSEDED, store.parse(previous));
            }
        }
    }

    private FinalGradeHistory history(UUID projectId, String owner) {
        List<StoredRecord> records = GradingSupport.allNewestFirst(store, FINAL_GRADE, owner, projectId.toString());
        StoredRecord current = records.stream().filter(record -> CURRENT.equals(record.getStatus())).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No final grade has been recorded."));
        ObjectNode result = store.object();
        result.set(CURRENT_KEY, GradingSupport.view(store, current));
        ArrayNode all = result.putArray(HISTORY);
        records.forEach(record -> all.add(GradingSupport.view(store, record)));
        return GradingSupport.convert(result, FinalGradeHistory.class);
    }
}
