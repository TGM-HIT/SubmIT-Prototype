package ac.at.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.application.ApplyingGroupRecords;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Immutable allocation input, separate from live invitations and membership. */
@Component
class ApplyingUnitSnapshots {
    private final RecordStore store;
    private final ApplyingGroupRecords groups;

    ApplyingUnitSnapshots(RecordStore store, ApplyingGroupRecords groups) {
        this.store = store;
        this.groups = groups;
    }

    List<StoredRecord> submitted(UUID periodId) {
        return groups.groups(periodId).stream().filter(group -> "SUBMITTED".equals(group.getStatus())).toList();
    }

    ArrayNode serialize(List<StoredRecord> records) {
        ArrayNode units = store.object().arrayNode();
        for (StoredRecord group : records) {
            ObjectNode data = store.parse(group);
            ObjectNode unit = units.addObject().put("groupId", group.getId().toString())
                    .put("revision", data.path("revision").asLong());
            unit.set("studentIds", data.path("members").deepCopy());
            unit.set("projectIds", data.path("projectIds").deepCopy());
        }
        return units;
    }

    void validate(StoredRecord group) {
        ObjectNode data = store.parse(group);
        List<String> members = groups.ids(data.path("members"));
        if (members.size() < 2 || members.size() > ApplyingGroupRecords.MAX_MEMBERS) {
            throw new IllegalArgumentException("Invalid shared allocation unit size.");
        }
        for (String member : members) {
            try {
                groups.student(member);
            } catch (ac.at.tgm.submit.web.ApiException exception) {
                throw new IllegalArgumentException("Invalid shared allocation unit: a member is no longer a student.", exception);
            }
            if (data.path("approvals").path(member).asLong(-1) != data.path("revision").asLong()) {
                throw new IllegalArgumentException("Invalid shared allocation unit: missing revision approval.");
            }
        }
    }

    void expandPins(List<StoredRecord> records, Map<String, String> pins) {
        for (StoredRecord group : records) {
            if (ApplyingGroupRecords.TYPE.equals(group.getType())) {
                validate(group);
                List<String> members = groups.ids(store.parse(group).path("members"));
                List<String> targets = members.stream().filter(pins::containsKey).map(pins::get).distinct().toList();
                if (targets.size() > 1) {
                    throw new IllegalArgumentException("Shared allocation unit has conflicting pins.");
                }
                if (!targets.isEmpty()) {
                    members.forEach(member -> pins.put(member, targets.getFirst()));
                }
            }
        }
    }

    void persistOrigin(ObjectNode group, JsonNode run) {
        ArrayNode origins = group.putArray("applyingUnits");
        List<String> students = groups.ids(group.path("studentIds"));
        for (JsonNode unit : run.path("applyingUnits")) {
            if (students.containsAll(groups.ids(unit.path("studentIds")))) {
                origins.add(unit.deepCopy());
            }
        }
        group.put("originAvailable", run.has("applyingUnits"));
    }
}
