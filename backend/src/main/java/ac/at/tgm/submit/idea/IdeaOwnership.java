package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos;
import ac.at.tgm.submit.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaOwnership {
    private static final String IDEA = "idea";
    private static final String OWNER_IDS = "ownerIds";
    private static final String COLLABORATOR_IDS = "collaboratorIds";
    private static final int MAX_OWNERS = 5;
    private final RecordStore store;
    private final AuditService audit;
    private final IdeaResponseMapper mapper;
    private final UserAccountRepository users;

    IdeaOwnership(RecordStore store, AuditService audit, IdeaResponseMapper mapper, UserAccountRepository users) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.users = users;
    }

    ObjectNode prepareCreate(ObjectNode request, UUID creator, Role creatorRole) {
        if (creatorRole == Role.TEACHER && request.path(COLLABORATOR_IDS).isArray()
                && !request.path(COLLABORATOR_IDS).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Teacher ideas have one owner and cannot have collaborators.");
        }
        List<UUID> collaborators = requestedCollaborators(request, creator);
        ObjectNode payload = request.deepCopy();
        payload.remove(COLLABORATOR_IDS);
        List<UUID> owners = new ArrayList<>();
        owners.add(creator);
        owners.addAll(collaborators);
        payload.set(OWNER_IDS, store.value(owners.stream().map(UUID::toString).toList()));
        return payload;
    }

    void preserveOwners(StoredRecord idea, ObjectNode payload) {
        payload.remove(COLLABORATOR_IDS);
        payload.set(OWNER_IDS, store.value(ownerIds(idea).stream().map(UUID::toString).toList()));
    }

    List<UUID> ownerIds(StoredRecord idea) {
        UUID creator = UUID.fromString(idea.getOwnerId());
        List<UUID> owners = new ArrayList<>();
        owners.add(creator);
        JsonNode stored = store.parse(idea).path(OWNER_IDS);
        if (stored.isArray()) {
            for (JsonNode value : stored) {
                UUID id = UUID.fromString(value.asText());
                if (!owners.contains(id)) {
                    owners.add(id);
                }
            }
        }
        return owners;
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse addOwner(AuthenticatedUser caller, UUID id, UUID studentId) {
        StoredRecord idea = findIdeaForUpdate(id);
        requireCreator(caller, idea);
        List<UUID> owners = ownerIds(idea);
        validateStudent(studentId);
        if (owners.contains(studentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This student is already an owner.");
        }
        if (owners.size() >= MAX_OWNERS) {
            throw new ApiException(HttpStatus.CONFLICT, "An idea can have at most five owners.");
        }
        owners.add(studentId);
        persistOwners(idea, owners);
        audit.record(caller, "IDEA_OWNER_ADDED", IDEA, id);
        return mapper.response(idea, true, caller.role());
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse removeOwner(AuthenticatedUser caller, UUID id, UUID studentId) {
        StoredRecord idea = findIdeaForUpdate(id);
        requireCreator(caller, idea);
        if (studentId.equals(UUID.fromString(idea.getOwnerId()))) {
            throw new ApiException(HttpStatus.CONFLICT, "The creator cannot be removed from the idea.");
        }
        List<UUID> owners = ownerIds(idea);
        if (!owners.remove(studentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "The student is not an owner of this idea.");
        }
        persistOwners(idea, owners);
        audit.record(caller, "IDEA_OWNER_REMOVED", IDEA, id);
        return mapper.response(idea, true, caller.role());
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public void leave(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdeaForUpdate(id);
        if (caller.id().toString().equals(idea.getOwnerId())) {
            throw new ApiException(HttpStatus.CONFLICT, "The creator cannot leave the idea.");
        }
        List<UUID> owners = ownerIds(idea);
        if (!owners.remove(caller.id())) {
            throw new org.springframework.security.access.AccessDeniedException("This resource belongs to another user.");
        }
        persistOwners(idea, owners);
        audit.record(caller, "IDEA_OWNER_LEFT", IDEA, id);
    }

    private List<UUID> requestedCollaborators(ObjectNode request, UUID creator) {
        JsonNode requested = request.path(COLLABORATOR_IDS);
        if (!requested.isMissingNode() && !requested.isNull() && !requested.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, COLLABORATOR_IDS + " must be a list of student IDs.");
        }
        List<UUID> ids = new ArrayList<>();
        if (requested.isArray()) {
            for (JsonNode value : requested) {
                UUID id = parseId(value);
                validateNotCreator(id, creator);
                validateUnique(id, ids);
                validateStudent(id);
                ids.add(id);
            }
        }
        return ids;
    }

    private UUID parseId(JsonNode value) {
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Each collaborator must be a valid student ID.");
        }
    }

    private void validateNotCreator(UUID id, UUID creator) {
        if (id.equals(creator)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The creator is already an owner.");
        }
    }

    private void validateUnique(UUID id, List<UUID> ids) {
        if (ids.contains(id)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate collaborators are not allowed.");
        }
    }

    private void validateStudent(UUID id) {
        UserAccount student = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The selected student was not found."));
        if (!student.hasRole(Role.STUDENT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only student accounts can own an idea.");
        }
    }

    private void persistOwners(StoredRecord idea, List<UUID> owners) {
        ObjectNode payload = store.parse(idea);
        payload.set(OWNER_IDS, store.value(owners.stream().map(UUID::toString).toList()));
        store.update(idea, idea.getStatus(), payload);
    }

    private void requireCreator(AuthenticatedUser caller, StoredRecord idea) {
        if (!caller.id().toString().equals(idea.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the idea creator may manage its owners.");
        }
    }

    private StoredRecord findIdeaForUpdate(UUID id) {
        return store.repository().findByIdForUpdate(id).filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Idea was not found."));
    }
}
