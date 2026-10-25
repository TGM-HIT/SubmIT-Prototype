package ac.at.tgm.submit.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.ElectiveSubjectRepository;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos.AuditEntryResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditContextResolver {
    private static final String ACTOR_ID = "actorId";
    private static final String ACTOR_USERNAME = "actorUsername";
    private static final String ACTOR_NAME = "actorDisplayName";
    private static final String ACTOR_ROLE = "actorRole";
    private static final String ACTION = "action";
    private static final String ENTITY_TYPE = "entityType";
    private static final String ENTITY_ID = "entityId";
    private static final String OCCURRED_AT = "occurredAt";
    private static final String RESOURCE_LABEL = "resourceLabel";
    private static final String SYSTEM = "SYSTEM";
    private static final String IDEA = "idea";
    private static final String USER = "user";
    private static final String STUDENT_CLASS = "student_class";
    private static final String ELECTIVE_SUBJECT = "elective_subject";
    private static final String REVIEW = "review";
    private static final String PROJECT = "project";
    private static final String PROJECT_ID = "projectId";
    private static final String PREFERENCES = "preferences";
    private static final String FORMAL_APPLICATION = "formal_application";

    private final RecordStore store;
    private final UserAccountRepository users;
    private final StudentClassRepository classes;
    private final ElectiveSubjectRepository subjects;

    public AuditContextResolver(RecordStore store, UserAccountRepository users, StudentClassRepository classes,
            ElectiveSubjectRepository subjects) {
        this.store = store;
        this.users = users;
        this.classes = classes;
        this.subjects = subjects;
    }

    public void snapshotActor(ObjectNode payload, AuthenticatedUser actor) {
        users.findById(actor.id()).ifPresent(user -> payload.put(ACTOR_NAME, user.getDisplayName()));
        payload.put(ACTOR_ROLE, actor.role().name());
    }

    public String snapshotResource(String entityType, UUID entityId) {
        return resourceLabel(entityType, entityId);
    }

    public List<AuditEntryResponse> responses(List<StoredRecord> records) {
        References references = references(records);
        Context context = resolve(references);
        List<AuditEntryResponse> responses = new ArrayList<>();
        for (StoredRecord record : records) {
            responses.add(response(store.parse(record), context));
        }
        return responses;
    }

    private References references(List<StoredRecord> records) {
        Set<UUID> actorIds = new HashSet<>();
        Map<String, Set<UUID>> resources = new HashMap<>();
        for (StoredRecord record : records) {
            ObjectNode event = store.parse(record);
            collectActor(event, actorIds);
            collectResource(event, resources);
        }
        return new References(actorIds, resources);
    }

    private void collectActor(ObjectNode event, Set<UUID> actorIds) {
        boolean hasEventTimeDetails = event.hasNonNull(ACTOR_NAME) && event.hasNonNull(ACTOR_ROLE);
        if (!SYSTEM.equals(event.path(ACTOR_USERNAME).asText()) && !hasEventTimeDetails && event.hasNonNull(ACTOR_ID)) {
            actorIds.add(UUID.fromString(event.path(ACTOR_ID).asText()));
        }
    }

    private void collectResource(ObjectNode event, Map<String, Set<UUID>> resources) {
        if (textOrNull(event, RESOURCE_LABEL) != null) {
            return;
        }
        String type = storedType(event.path(ENTITY_TYPE).asText());
        if (type != null) {
            resources.computeIfAbsent(type, ignored -> new HashSet<>())
                    .add(UUID.fromString(event.path(ENTITY_ID).asText()));
        }
        if (IDEA.equals(event.path(ENTITY_TYPE).asText())) {
            resources.computeIfAbsent(REVIEW, ignored -> new HashSet<>())
                    .add(UUID.fromString(event.path(ENTITY_ID).asText()));
        }
    }

    private Context resolve(References refs) {
        Map<UUID, UserAccount> actors = users.findAllById(refs.actors()).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, user -> user));
        Map<UUID, String> labels = new HashMap<>();
        loadJpaLabels(refs.resources(), labels);
        loadStoredLabels(refs.resources(), labels);
        return new Context(actors, labels);
    }

    private void loadJpaLabels(Map<String, Set<UUID>> resources, Map<UUID, String> labels) {
        classes.findAllById(resources.getOrDefault(STUDENT_CLASS, Set.of()))
                .forEach(value -> labels.put(value.getId(), value.getName()));
        subjects.findAllById(resources.getOrDefault(ELECTIVE_SUBJECT, Set.of()))
                .forEach(value -> labels.put(value.getId(), value.getName()));
        users.findAllById(resources.getOrDefault(USER, Set.of()))
                .forEach(value -> labels.put(value.getId(), value.getDisplayName()));
    }

    private void loadStoredLabels(Map<String, Set<UUID>> resources, Map<UUID, String> labels) {
        List<TypedResource> storedResources = new ArrayList<>();
        Set<UUID> storedIds = new HashSet<>();
        resources.forEach((type, ids) -> {
            if (!isJpaType(type)) {
                storedIds.addAll(ids);
            }
        });
        store.repository().findAllById(storedIds).stream()
                .filter(item -> resources.getOrDefault(item.getType(), Set.of()).contains(item.getId()))
                .forEach(item -> storedResources.add(new TypedResource(item.getType(), item)));
        Map<UUID, StoredRecord> related = relatedRecords(storedResources);
        storedResources.forEach(item -> addStoredLabel(item, related, labels));
    }

    private boolean isJpaType(String type) {
        return Set.of(USER, STUDENT_CLASS, ELECTIVE_SUBJECT).contains(type);
    }

    private Map<UUID, StoredRecord> relatedRecords(List<TypedResource> resources) {
        Set<UUID> ids = new HashSet<>();
        resources.forEach(item -> collectRelatedIds(item.record(), ids));
        Map<UUID, StoredRecord> related = new HashMap<>();
        store.repository().findAllById(ids).forEach(item -> related.put(item.getId(), item));
        Set<UUID> secondaryIds = new HashSet<>();
        related.values().forEach(record -> collectRelatedIds(record, secondaryIds));
        secondaryIds.removeAll(related.keySet());
        store.repository().findAllById(secondaryIds).forEach(item -> related.put(item.getId(), item));
        return related;
    }

    private void collectRelatedIds(StoredRecord record, Set<UUID> ids) {
        addUuid(record.getParentId(), ids);
        ObjectNode payload = store.parse(record);
        addUuid(textOrNull(payload, PROJECT_ID), ids);
        JsonNode preferences = payload.path(PREFERENCES);
        if (preferences.isArray() && !preferences.isEmpty() && preferences.get(0).isObject()) {
            addUuid(textOrNull((ObjectNode) preferences.get(0), PROJECT_ID), ids);
        }
        addUuid(textOrNull(payload, "rubricId"), ids);
    }

    private void addUuid(String value, Set<UUID> ids) {
        if (value != null) {
            ids.add(UUID.fromString(value));
        }
    }

    private void addStoredLabel(TypedResource resource, Map<UUID, StoredRecord> related, Map<UUID, String> labels) {
        String label = contextLabel(resource.type(), resource.record(), related);
        if (label != null) {
            labels.put(resource.record().getId(), label);
        }
    }

    private String contextLabel(String type, StoredRecord record, Map<UUID, StoredRecord> related) {
        ObjectNode payload = store.parse(record);
        String label = labelFrom(payload);
        if (label != null) {
            return label;
        }
        label = projectFromPayload(payload, related);
        if (label != null) {
            return label;
        }
        return parentContextLabel(type, record, related);
    }

    private String projectFromPayload(ObjectNode payload, Map<UUID, StoredRecord> related) {
        String id = textOrNull(payload, PROJECT_ID);
        JsonNode preferences = payload.path(PREFERENCES);
        if (id == null && preferences.isArray() && !preferences.isEmpty() && preferences.get(0).isObject()) {
            id = textOrNull((ObjectNode) preferences.get(0), PROJECT_ID);
        }
        StoredRecord project = id == null ? null : related.get(UUID.fromString(id));
        return project == null ? null : labelFrom(store.parse(project));
    }

    private String parentContextLabel(String type, StoredRecord record, Map<UUID, StoredRecord> related) {
        if (record.getParentId() == null) {
            return null;
        }
        StoredRecord parent = related.get(UUID.fromString(record.getParentId()));
        if (parent == null) {
            return null;
        }
        if (Set.of("student_application", "applying_group", "allocation_pin", "allocation_run")
                .contains(type)) {
            return labelFrom(store.parse(parent));
        }
        if (Set.of(FORMAL_APPLICATION, "student_grade", "group_grade", "final_grade", "metric", "artifact",
                "rubric_binding").contains(type)) {
            return labelFromParentProject(parent, related);
        }
        if ("specification".equals(type) || REVIEW.equals(type)) {
            return labelFromParentProject(parent, related);
        }
        return null;
    }

    private String labelFromParentProject(StoredRecord parent, Map<UUID, StoredRecord> related) {
        if (IDEA.equals(parent.getType()) || PROJECT.equals(parent.getType())) {
            return labelFrom(store.parse(parent));
        }
        if (FORMAL_APPLICATION.equals(parent.getType()) && parent.getParentId() != null) {
            StoredRecord project = related.get(UUID.fromString(parent.getParentId()));
            return project == null ? null : labelFrom(store.parse(project));
        }
        return null;
    }

    private AuditEntryResponse response(ObjectNode event, Context context) {
        validate(event);
        Actor actor = actor(event, context.actors());
        Resource resource = resource(event, context.labels());
        String actorId = SYSTEM.equals(event.path(ACTOR_USERNAME).asText()) ? null : textOrNull(event, ACTOR_ID);
        return new AuditEntryResponse(actorId, event.path(ACTOR_USERNAME).asText(),
                event.path(ACTION).asText(), event.path(ENTITY_TYPE).asText(), event.path(ENTITY_ID).asText(),
                event.path(OCCURRED_AT).asText(), actor.name(), actor.role(), resource.label(), actor.source(),
                resource.source());
    }

    private void validate(ObjectNode event) {
        for (String key : List.of(ACTOR_USERNAME, ACTION, ENTITY_TYPE, ENTITY_ID, OCCURRED_AT)) {
            JsonNode value = event.path(key);
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalStateException("Persisted audit event is missing required " + key + ".");
            }
        }
        Instant.parse(event.path(OCCURRED_AT).asText());
        if (event.hasNonNull(ACTOR_ID)) {
            UUID.fromString(event.path(ACTOR_ID).asText());
        }
    }

    private Actor actor(ObjectNode event, Map<UUID, UserAccount> currentActors) {
        if (SYSTEM.equals(event.path(ACTOR_USERNAME).asText())) {
            return new Actor(null, null, SYSTEM);
        }
        if (event.hasNonNull(ACTOR_NAME) && event.hasNonNull(ACTOR_ROLE)) {
            return new Actor(event.path(ACTOR_NAME).asText(), Role.valueOf(event.path(ACTOR_ROLE).asText()),
                    "EVENT_TIME");
        }
        UserAccount current = currentActor(event, currentActors);
        return current == null ? new Actor(null, null, "UNAVAILABLE")
                : new Actor(current.getDisplayName(), current.getRole(), "CURRENT");
    }

    private UserAccount currentActor(ObjectNode event, Map<UUID, UserAccount> currentActors) {
        String id = textOrNull(event, ACTOR_ID);
        return id == null ? null : currentActors.get(UUID.fromString(id));
    }

    private Resource resource(ObjectNode event, Map<UUID, String> currentLabels) {
        String label = textOrNull(event, RESOURCE_LABEL);
        if (label != null) {
            return new Resource(label, "EVENT_TIME");
        }
        String id = textOrNull(event, ENTITY_ID);
        label = id == null ? null : currentLabels.get(UUID.fromString(id));
        return label == null ? new Resource(null, "UNAVAILABLE") : new Resource(label, "CURRENT");
    }

    private String textOrNull(ObjectNode event, String field) {
        if (!event.hasNonNull(field) || !event.path(field).isTextual()) {
            return null;
        }
        String value = event.path(field).asText();
        return value.isBlank() ? null : value;
    }

    private String resourceLabel(String entityType, UUID id) {
        if (USER.equals(entityType)) {
            return users.findById(id).map(UserAccount::getDisplayName).orElse(null);
        }
        if (STUDENT_CLASS.equals(entityType)) {
            return classes.findById(id).map(value -> value.getName()).orElse(null);
        }
        if (ELECTIVE_SUBJECT.equals(entityType)) {
            return subjects.findById(id).map(value -> value.getName()).orElse(null);
        }
        return storedResourceLabel(entityType, id);
    }

    private String storedResourceLabel(String entityType, UUID id) {
        String type = storedType(entityType);
        if (type == null) {
            return null;
        }
        var stored = store.repository().findById(id).filter(record -> type.equals(record.getType()));
        if (stored.isPresent()) {
            ObjectNode payload = store.parse(stored.get());
            String label = labelFrom(payload);
            if (label != null) {
                return label;
            }
            String parentLabel = relatedProjectLabel(payload);
            if (parentLabel == null) {
                parentLabel = parentLabel(entityType, stored.get());
            }
            if (parentLabel != null) {
                return parentLabel;
            }
        }
        return entityType.equals(IDEA) ? reviewProjectLabel(id) : null;
    }

    private String parentLabel(String entityType, StoredRecord record) {
        if (record.getParentId() == null) {
            return null;
        }
        UUID parentId = UUID.fromString(record.getParentId());
        return switch (entityType) {
            case "student_application", "applying_group", "allocation_pin", "allocation_run" ->
                    labelFor(parentId, "application_period");
            case FORMAL_APPLICATION, "student_grade", "group_grade", "final_grade", "metric", "artifact",
                    "rubric_binding" -> projectLabel(parentId);
            case "specification" -> labelFor(parentId, IDEA);
            case REVIEW -> reviewedResourceLabel(parentId);
            default -> null;
        };
    }

    private String reviewProjectLabel(UUID id) {
        var review = store.repository().findById(id).filter(value -> REVIEW.equals(value.getType()));
        if (review.isEmpty() || review.get().getParentId() == null) {
            return null;
        }
        return reviewedResourceLabel(UUID.fromString(review.get().getParentId()));
    }

    private String reviewedResourceLabel(UUID parentId) {
        var parent = store.repository().findById(parentId);
        if (parent.isEmpty()) {
            return null;
        }
        if (IDEA.equals(parent.get().getType())) {
            return labelFrom(store.parse(parent.get()));
        }
        if (FORMAL_APPLICATION.equals(parent.get().getType())) {
            return projectLabel(UUID.fromString(parent.get().getParentId()));
        }
        return null;
    }

    private String projectLabel(UUID id) {
        String label = labelFor(id, IDEA);
        return label == null ? labelFor(id, "project") : label;
    }

    private String labelFor(UUID id, String type) {
        return store.repository().findById(id).filter(value -> type.equals(value.getType()))
                .map(value -> labelFrom(store.parse(value))).orElse(null);
    }

    private String relatedProjectLabel(ObjectNode payload) {
        String id = textOrNull(payload, PROJECT_ID);
        if (id == null && payload.path(PREFERENCES).isArray() && !payload.path(PREFERENCES).isEmpty()) {
            id = textOrNull((ObjectNode) payload.path(PREFERENCES).get(0), PROJECT_ID);
        }
        return id == null ? null : projectLabel(UUID.fromString(id));
    }

    private String labelFrom(ObjectNode payload) {
        for (String key : new String[] {"title", "name", "projectTitle", "subjectName", "className",
                "rubricName", "periodName", "label", "filename", "fileName", "originalFilename"}) {
            if (payload.hasNonNull(key) && !payload.path(key).asText().isBlank()) {
                return payload.path(key).asText();
            }
        }
        String rubricName = textOrNull(payload, "rubricName");
        if (rubricName != null) {
            return rubricName;
        }
        String version = textOrNull(payload, "version");
        if (version != null) {
            return version;
        }
        String schoolYear = textOrNull(payload, "schoolYear");
        if (schoolYear != null) {
            return schoolYear;
        }
        return null;
    }

    private String storedType(String entityType) {
        return switch (entityType) {
            case USER, STUDENT_CLASS, ELECTIVE_SUBJECT -> entityType;
            case "project" -> IDEA;
            case REVIEW -> REVIEW;
            case "student_application", "application_period", "allocation_run", FORMAL_APPLICATION,
                    "artifact", IDEA, "specification", "applying_group", "allocation_pin", "rubric",
                    "rubric_binding", "student_grade", "group_grade", "final_grade", "metric" -> entityType;
            default -> null;
        };
    }

    private record References(Set<UUID> actors, Map<String, Set<UUID>> resources) {
    }

    private record Context(Map<UUID, UserAccount> actors, Map<UUID, String> labels) {
    }

    private record Actor(String name, Role role, String source) {
    }

    private record Resource(String label, String source) {
    }

    private record TypedResource(String type, StoredRecord record) {
    }
}
