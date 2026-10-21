package ac.at.tgm.submit.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordStore {
    private final StoredRecordRepository records;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    public RecordStore(StoredRecordRepository records, ObjectMapper objectMapper,
            jakarta.persistence.EntityManager entityManager) {
        this.records = records;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public StoredRecord create(String type, String ownerId, String parentId, String status, JsonNode payload) {
        StoredRecord.Details details = new StoredRecord.Details(type, ownerId, parentId, status, null,
                encode(payload), Instant.now());
        return records.save(new StoredRecord(UUID.randomUUID(), details));
    }

    @Transactional
    public StoredRecord createAudit(String ownerId, String parentId, String status, JsonNode payload,
            String referenceType) {
        StoredRecord.Details details = new StoredRecord.Details("audit", ownerId, parentId, status,
                referenceType, encode(payload), Instant.now());
        return records.save(new StoredRecord(UUID.randomUUID(), details));
    }

    @Transactional(readOnly = true)
    public StoredRecord get(String type, UUID id) {
        return records.findById(id).filter(record -> record.getType().equals(type))
                .orElseThrow(() -> new EntityNotFoundException(type + " was not found."));
    }

    @Transactional(readOnly = true)
    public Page<StoredRecord> page(String type, String ownerId, String parentId, Pageable pageable) {
        if (ownerId != null && parentId != null) {
            return records.findByTypeAndOwnerIdAndParentId(type, ownerId, parentId, pageable);
        }
        if (ownerId != null) {
            return records.findByTypeAndOwnerId(type, ownerId, pageable);
        }
        if (parentId != null) {
            return records.findByTypeAndParentId(type, parentId, pageable);
        }
        return records.findByType(type, pageable);
    }

    @Transactional
    public void update(StoredRecord record, String status, JsonNode payload) {
        record.setStatus(status);
        record.setPayload(encode(payload));
        records.save(record);
    }

    @Transactional
    public void delete(StoredRecord record) {
        records.delete(record);
    }

    public ObjectNode parse(StoredRecord record) {
        try {
            return (ObjectNode) objectMapper.readTree(record.getPayload());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted " + record.getType() + " data is invalid.", exception);
        }
    }

    public ObjectNode object() {
        return objectMapper.createObjectNode();
    }

    public JsonNode value(Object value) {
        return objectMapper.valueToTree(value);
    }

    public String encode(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist domain data.", exception);
        }
    }

    public StoredRecordRepository repository() {
        return records;
    }

    @Transactional
    public StoredRecord lock(String type, UUID id) {
        StoredRecord record = get(type, id);
        entityManager.flush();
        entityManager.refresh(record, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return record;
    }

    @Transactional
    public void refresh(StoredRecord record) {
        entityManager.refresh(record);
    }
}
