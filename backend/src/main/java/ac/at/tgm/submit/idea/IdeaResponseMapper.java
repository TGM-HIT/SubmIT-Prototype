package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.StudentDetailsService;
import ac.at.tgm.submit.web.ApiDtos.IdeaResponse;
import ac.at.tgm.submit.web.ApiDtos.IdeaOwnerResponse;
import ac.at.tgm.submit.web.ApiDtos.ReviewResponse;
import ac.at.tgm.submit.web.ApiDtos.SpecificationResponse;
import ac.at.tgm.submit.web.ApiDtos.SupervisionPreferenceResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentDetailsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class IdeaResponseMapper {
    private static final String SPECIFICATION = "specification";
    private static final String REVIEW = "review";
    private static final String SUPERVISION = "supervision_preference";
    private static final String OWNER_IDS = "ownerIds";

    private record ResponseContext(Map<UUID, String> names, String supervisorDisplayName, Role viewerRole) {
    }

    private final RecordStore store;
    private final UserAccountRepository users;
    private final StudentDetailsService studentDetails;

    IdeaResponseMapper(RecordStore store, UserAccountRepository users, StudentDetailsService studentDetails) {
        this.store = store;
        this.users = users;
        this.studentDetails = studentDetails;
    }

    IdeaResponse response(StoredRecord record, boolean includeHistory) {
        return response(record, includeHistory, null);
    }

    IdeaResponse response(StoredRecord record, boolean includeHistory, Role viewerRole) {
        List<StoredRecord> reviews = includeHistory ? children(REVIEW, record) : List.of();
        List<StoredRecord> preferences = includeHistory ? currentPreferences(record) : List.of();
        Set<UUID> userIds = new java.util.LinkedHashSet<>();
        userIds.add(UUID.fromString(record.getOwnerId()));
        ownerIds(record).forEach(userIds::add);
        reviews.stream().map(child -> UUID.fromString(child.getOwnerId())).forEach(userIds::add);
        preferences.stream().map(child -> UUID.fromString(child.getOwnerId())).forEach(userIds::add);
        return response(record, includeHistory, reviews, preferences,
                new ResponseContext(displayNames(userIds), supervisorDisplayName(record, viewerRole), viewerRole));
    }

    private IdeaResponse response(StoredRecord record, boolean includeHistory, List<StoredRecord> reviewRecords,
            List<StoredRecord> preferenceRecords, ResponseContext context) {
        Map<UUID, String> names = context.names();
        Role viewerRole = context.viewerRole();
        ObjectNode payload = store.parse(record);
        SpecificationResponse specification = null;
        List<ReviewResponse> reviews = List.of();
        List<SupervisionPreferenceResponse> preferences = List.of();
        if (includeHistory) {
            StoredRecord currentSpecification = store.repository()
                    .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, record.getId().toString())
                    .orElse(null);
            if (currentSpecification != null) {
                specification = specificationResponse(currentSpecification);
            }
            reviews = reviewRecords.stream().map(review -> reviewResponse(review, names)).toList();
            preferences = preferenceRecords.stream().map(preference -> supervisionResponse(preference, names)).toList();
        }
        UUID ownerId = UUID.fromString(record.getOwnerId());
        List<IdeaOwnerResponse> owners = ownerIds(record).stream()
                .map(id -> ownerResponse(id, names.get(id), id.equals(ownerId), viewerRole)).toList();
        Boolean teacherIdea = null;
        if (viewerRole != null && viewerRole != Role.STUDENT) {
            teacherIdea = users.findById(ownerId).map(UserAccount::getRole).orElse(null) == Role.TEACHER;
        }

        return new IdeaResponse(record.getId(), ownerId, ownerId, owners, names.get(ownerId), record.getStatus(),
                payload.path("title").asText(), payload.path("description").asText(),
                payload.path("intendedOutcome").asText(), strings(payload, "scopePoints"),
                strings(payload, "learningTopics"), payload.path("schoolYear").asText(),
                context.supervisorDisplayName(), teacherIdea, specification, reviews, preferences);
    }

    private IdeaOwnerResponse ownerResponse(UUID id, String name, boolean creator, Role viewerRole) {
        StudentDetailsResponse details = viewerRole == Role.ADMIN || viewerRole == Role.TEACHER
                ? users.findById(id).filter(user -> user.hasRole(Role.STUDENT))
                        .map(studentDetails::response).orElse(null)
                : null;
        return new IdeaOwnerResponse(id, name, creator,
                details == null ? null : details.className(),
                details == null ? null : details.electiveSubjectNames());
    }

    List<IdeaResponse> responses(List<StoredRecord> records, Role viewerRole) {
        Set<UUID> ownerIds = records.stream().flatMap(record -> ownerIds(record).stream())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, String> names = displayNames(ownerIds);
        return records.stream()
                .map(record -> response(record, false, List.of(), List.of(),
                        new ResponseContext(names, supervisorDisplayName(record, viewerRole), viewerRole)))
                .toList();
    }

    private List<UUID> ownerIds(StoredRecord record) {
        UUID creator = UUID.fromString(record.getOwnerId());
        List<UUID> ids = new ArrayList<>();
        ids.add(creator);
        var storedOwners = store.parse(record).path(OWNER_IDS);
        if (storedOwners.isArray()) {
            storedOwners.forEach(value -> {
                UUID id = UUID.fromString(value.asText());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            });
        }
        return ids;
    }

    private String supervisorDisplayName(StoredRecord idea, Role viewerRole) {
        if (viewerRole != Role.ADMIN && viewerRole != Role.TEACHER) {
            return null;
        }
        return store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "confirmed_supervisor", idea.getId().toString())
                .map(store::parse)
                .map(assignment -> assignment.path("teacherId").asText(null))
                .filter(id -> id != null && !id.isBlank())
                .map(UUID::fromString)
                .flatMap(users::findById)
                .map(user -> user.getDisplayName())
                .orElse(null);
    }

    SpecificationResponse specificationResponse(StoredRecord record) {
        ObjectNode payload = store.parse(record);
        return new SpecificationResponse(record.getId(), record.getStatus(),
                strings(payload, "objectives"), strings(payload, "intendedUsers"),
                strings(payload, "requirements"), strings(payload, "deliverables"),
                payload.path("scope").asText(), strings(payload, "dependencies"),
                payload.path("revisionId").asText(null));
    }

    ReviewResponse reviewResponse(StoredRecord record) {
        return reviewResponse(record, displayNames(Set.of(UUID.fromString(record.getOwnerId()))));
    }

    private ReviewResponse reviewResponse(StoredRecord record, Map<UUID, String> names) {
        ObjectNode payload = store.parse(record);
        UUID teacherId = UUID.fromString(record.getOwnerId());
        return new ReviewResponse(record.getId(), teacherId, names.get(teacherId),
                reviewType(record, payload), record.getStatus(), payload.path("feedback").asText(),
                integer(payload, "workSize"),
                integer(payload, "curriculumRelevance"), integer(payload, "technicalComplexity"),
                integer(payload, "feasibility"), integer(payload, "learningValue"),
                integer(payload, "resourceNeeds"), integer(payload, "riskAndDependencies"),
                payload.path("recommendation").asText(null));
    }

    private String reviewType(StoredRecord record, ObjectNode payload) {
        String reviewType = payload.path("reviewType").asText(null);
        if (reviewType != null) {
            return reviewType;
        }
        return switch (record.getStatus()) {
            case "APPROVE_FOR_SPECIFICATION" -> "IDEA";
            case "ACCEPT_FOR_APPLICATIONS" -> "SPECIFICATION";
            default -> "UNKNOWN";
        };
    }

    SupervisionPreferenceResponse supervisionResponse(StoredRecord record) {
        return supervisionResponse(record, displayNames(Set.of(UUID.fromString(record.getOwnerId()))));
    }

    private SupervisionPreferenceResponse supervisionResponse(StoredRecord record, Map<UUID, String> names) {
        ObjectNode payload = store.parse(record);
        UUID teacherId = UUID.fromString(record.getOwnerId());
        return new SupervisionPreferenceResponse(record.getId(), teacherId, names.get(teacherId),
                record.getStatus(), payload.path("note").asText(null));
    }

    private Map<UUID, String> displayNames(Set<UUID> userIds) {
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
    }

    private List<StoredRecord> children(String type, StoredRecord parent) {
        return store.page(type, null, parent.getId().toString(), PageRequest.of(0, 100)).getContent();
    }

    private List<StoredRecord> currentPreferences(StoredRecord parent) {
        Set<String> teacherIds = new java.util.HashSet<>();
        return store.repository().findByTypeAndParentIdOrderByCreatedAtDesc(SUPERVISION, parent.getId().toString())
                .stream()
                .filter(preference -> teacherIds.add(preference.getOwnerId()))
                .toList();
    }

    private Integer integer(ObjectNode payload, String name) {
        return payload.hasNonNull(name) ? payload.path(name).asInt() : null;
    }

    private List<String> strings(ObjectNode payload, String field) {
        List<String> values = new ArrayList<>();
        payload.path(field).forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }
}
