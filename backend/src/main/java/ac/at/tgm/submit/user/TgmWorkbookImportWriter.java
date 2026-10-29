package ac.at.tgm.submit.user;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.user.TgmApplicationImportWriter.ImportResult;
import ac.at.tgm.submit.user.TgmIdentityImportWriter.ImportedIdentities;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Coordinates writes and persists source ownership/checksum only after the complete fixture succeeds. */
@Component
class TgmWorkbookImportWriter {
    private static final System.Logger LOGGER = System.getLogger(TgmWorkbookImportWriter.class.getName());
    private static final String IMPORT_TYPE = "testing_tgm_workbook_import";
    private static final String IMPORT_SOURCE = "source";
    private static final String GROUP_PREFIX = "group:";
    private final RecordStore store;
    private final TgmIdentityImportWriter identities;
    private final TgmProjectImportWriter projects;
    private final TgmApplicationImportWriter applications;

    TgmWorkbookImportWriter(RecordStore store, TgmIdentityImportWriter identities,
            TgmProjectImportWriter projects, TgmApplicationImportWriter applications) {
        this.store = store;
        this.identities = identities;
        this.projects = projects;
        this.applications = applications;
    }

    boolean alreadyImported(String checksum) {
        var existing = store.repository().findByTypeAndParentId(IMPORT_TYPE, IMPORT_SOURCE);
        if (existing.isEmpty()) {
            return false;
        }
        if (existing.size() != 1 || !checksum.equals(store.parse(existing.getFirst()).path("sha256").asText())) {
            throw new IllegalStateException("TGM workbook checksum differs from the completed import in this "
                    + "database. Start with a fresh disposable database; existing records were not changed.");
        }
        logReusedImport(store.parse(existing.getFirst()));
        return true;
    }

    void write(String checksum, TgmWorkbookReader.WorkbookData workbook, TgmImportPlan plan) {
        ImportedIdentities imported = identities.write(plan);
        Map<String, UUID> projectIds = projects.write(plan.projectTitles(), imported);
        UserAccount admin = imported.staff().get("admin01");
        ImportResult applicationResult = applications.write(plan, imported, projectIds, admin);
        validateReconciliation(plan, applicationResult.groupedStudents());
        store.create(IMPORT_TYPE, admin.getId().toString(), IMPORT_SOURCE, "COMPLETE",
                provenance(new ImportWriteState(checksum, workbook, plan, projectIds, applicationResult, imported)));
        logSummary(checksum, workbook, plan, applicationResult.groupedStudents());
        logSkipped(plan.invalidReasons());
    }

    private ObjectNode provenance(ImportWriteState state) {
        TgmImportPlan plan = state.plan();
        ObjectNode ownership = store.object().put("sha256", state.checksum())
                .put("periodId", state.result().periodId().toString())
                .put("studentCount", plan.students().size()).put("projectCount", state.projectIds().size())
                .put("validRankingCount", validRankingCount(plan))
                .put("invalidRankingCount", invalidRankingCount(plan))
                .put("groupCount", plan.groups().size())
                .put("groupedStudentCount", state.result().groupedStudents())
                .put("soloCount", plan.solos().size())
                .put("skippedDeclarationCount", skippedDeclarationCount(plan))
                .put("skippedApplicationCount", skippedApplicationCount(plan));
        ObjectNode classes = ownership.putObject("classCounts");
        TgmWorkbookReader.SHEETS.forEach(sheet -> classes.put(sheet,
                state.workbook().classCounts().getOrDefault(sheet, 0)));
        ArrayNode studentProvenance = ownership.putArray("studentSourceCoordinates");
        plan.students().forEach(student -> studentProvenance.addObject().put("sheet", student.sheet())
                .put("row", student.row())
                .put("accountId", state.imported().students().get(coordinate(student)).getId().toString()));
        ArrayNode projectProvenance = ownership.putArray("projectIds");
        state.projectIds().values().forEach(id -> projectProvenance.add(id.toString()));
        return ownership;
    }

    private void validateReconciliation(TgmImportPlan plan, int groupedStudents) {
        long noApplication = plan.students().stream()
                .filter(student -> !Boolean.TRUE.equals(plan.validRankings().get(coordinate(student))))
                .count();
        if (plan.students().size() != plan.solos().size() + groupedStudents + noApplication) {
            throw new IllegalStateException("TGM import reconciliation invariant failed; transaction rolled back.");
        }
    }

    private long validRankingCount(TgmImportPlan plan) {
        return plan.validRankings().values().stream().filter(Boolean::booleanValue).count();
    }

    private long invalidRankingCount(TgmImportPlan plan) {
        return plan.invalidReasons().keySet().stream().filter(key -> !key.startsWith(GROUP_PREFIX)).count();
    }

    private long skippedDeclarationCount(TgmImportPlan plan) {
        return plan.invalidReasons().keySet().stream().filter(key -> key.startsWith(GROUP_PREFIX)).count();
    }

    private long skippedApplicationCount(TgmImportPlan plan) {
        return invalidRankingCount(plan);
    }

    private String coordinate(TgmWorkbookReader.StudentRow row) {
        return row.sheet() + "!" + row.row();
    }

    private void logSummary(String checksum, TgmWorkbookReader.WorkbookData workbook, TgmImportPlan plan,
            int groupedStudents) {
        LOGGER.log(System.Logger.Level.INFO,
                "TGM import complete: sha256={0}, students={1}, classes={2}, projects={3}, validRankings={4}, "
                        + "invalidRankings={5}, groups={6}, groupedStudents={7}, effectiveSolos={8}, "
                        + "studentsWithoutApplication={9}, skippedDeclarations={10}.",
                checksum, plan.students().size(), workbook.classCounts(), plan.projectTitles().size(),
                validRankingCount(plan), invalidRankingCount(plan), plan.groups().size(), groupedStudents,
                plan.solos().size(), plan.students().size() - plan.solos().size() - groupedStudents,
                skippedDeclarationCount(plan));
    }

    private void logSkipped(Map<String, String> reasons) {
        reasons.forEach((coordinate, reason) -> logSkippedReason(coordinate, reason));
    }

    private void logSkippedReason(String coordinate, String reason) {
        if (coordinate.startsWith(GROUP_PREFIX)) {
            LOGGER.log(System.Logger.Level.WARNING, "TGM skipped group declaration at {0}: {1}.",
                    coordinate.substring(GROUP_PREFIX.length()), reason);
        } else {
            LOGGER.log(System.Logger.Level.WARNING, "TGM skipped application ranking at {0}: {1}.",
                    coordinate, reason);
        }
    }

    private void logReusedImport(ObjectNode marker) {
        LOGGER.log(System.Logger.Level.INFO,
                "TGM import already completed: sha256={0}, classes={1}, students={2}, projects={3}, "
                        + "validRankings={4}, invalidRankings={5}, groups={6}, groupedStudents={7}, "
                        + "effectiveSolos={8}, skippedDeclarations={9}, skippedApplications={10}; no records "
                        + "were replayed.",
                marker.path("sha256").asText(), marker.path("classCounts"), marker.path("studentCount").asInt(),
                marker.path("projectCount").asInt(), marker.path("validRankingCount").asInt(),
                marker.path("invalidRankingCount").asInt(), marker.path("groupCount").asInt(),
                marker.path("groupedStudentCount").asInt(), marker.path("soloCount").asInt(),
                marker.path("skippedDeclarationCount").asInt(), marker.path("skippedApplicationCount").asInt());
    }

    private record ImportWriteState(String checksum, TgmWorkbookReader.WorkbookData workbook, TgmImportPlan plan,
            Map<String, UUID> projectIds, ImportResult result, ImportedIdentities imported) {
    }
}
