package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.CURRENT;
import static at.ac.tgm.submit.grading.GradingKeys.CURRENT_KEY;
import static at.ac.tgm.submit.grading.GradingKeys.DECIDED_AT;
import static at.ac.tgm.submit.grading.GradingKeys.DECIDED_BY;
import static at.ac.tgm.submit.grading.GradingKeys.FINAL_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.HISTORY;
import static at.ac.tgm.submit.grading.GradingKeys.INPUTS;
import static at.ac.tgm.submit.grading.GradingKeys.PROJECT;
import static at.ac.tgm.submit.grading.GradingKeys.PROJECT_OWNER;
import static at.ac.tgm.submit.grading.GradingKeys.RULE_ID;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_VERSION;
import static at.ac.tgm.submit.grading.GradingKeys.SCOPE;
import static at.ac.tgm.submit.grading.GradingKeys.SCOPE_PROJECT;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENTS;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_ID;
import static at.ac.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static at.ac.tgm.submit.grading.GradingKeys.SUPERSEDED;
import static at.ac.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.grading.FinalGradeDtos.FinalGradeHistory;
import at.ac.tgm.submit.grading.FinalGradeDtos.FinalGradesRequest;
import at.ac.tgm.submit.grading.FinalGradeDtos.FinalGradesResponse;
import at.ac.tgm.submit.security.AuthenticatedUser;
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
        access.lockProject(projectId);
        RubricRef rubric = binding.requireSelected(projectId, AssessmentType.FINAL, request);
        DecisionPlan[] plans = plans(caller, projectId, rubric, request, studentEntries);
        GradingSupport.requireProjectWorkApproval(store, projectId);
        ObjectNode result = store.object();
        int planIndex = 0;
        if (request.has(PROJECT)) {
            result.set(PROJECT, GradingSupport.view(store, persist(caller, projectId, plans[planIndex++])));
        }
        ArrayNode students = result.putArray(STUDENTS);
        while (planIndex < plans.length && plans[planIndex] != null) {
            students.add(GradingSupport.view(store, persist(caller, projectId, plans[planIndex++])));
        }
        return GradingSupport.convert(result, FinalGradesResponse.class);
    }

    private DecisionPlan[] plans(AuthenticatedUser caller, UUID projectId, RubricRef rubric,
            JsonNode request, JsonNode studentEntries) {
        DecisionPlan[] plans = new DecisionPlan[1 + studentEntries.size()];
        int nextPlan = 0;
        if (request.has(PROJECT)) {
            plans[nextPlan++] = plan(caller, projectId, rubric, request.path(PROJECT), null);
        }
        for (JsonNode entry : studentEntries) {
            plans[nextPlan++] = plan(caller, projectId, rubric, entry, entry.path(STUDENT_ID).asText());
        }
        return plans;
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

    private DecisionPlan plan(AuthenticatedUser caller, UUID projectId, RubricRef rubric, JsonNode entry,
            String studentId) {
        GradingSupport.requireObject(entry);
        ObjectNode considered = inputs.collect(projectId, studentId, rubric.id(),
                binding.selected(projectId, AssessmentType.ROUTINE));
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
        return new DecisionPlan(studentId == null ? PROJECT_OWNER : studentId, payload);
    }

    private StoredRecord persist(AuthenticatedUser caller, UUID projectId, DecisionPlan plan) {
        plan.payload().put(DECIDED_BY, caller.id().toString());
        plan.payload().put(DECIDED_AT, GradingSupport.nowText());
        String owner = plan.owner();
        supersedeCurrent(projectId, owner);
        StoredRecord record = store.create(FINAL_GRADE, owner, projectId.toString(), CURRENT, plan.payload());
        audit.record(caller, "FINAL_GRADE_RECORDED", FINAL_GRADE, record.getId());
        return record;
    }

    private record DecisionPlan(String owner, ObjectNode payload) {
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
