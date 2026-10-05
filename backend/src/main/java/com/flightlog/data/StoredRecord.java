package com.flightlog.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domain_records")
public class StoredRecord {
    public record Details(String type, String ownerId, String parentId, String status, String referenceType,
            String payload, Instant createdAt) {
    }

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(length = 36)
    private String ownerId;

    @Column(length = 36)
    private String parentId;

    @Column(length = 40)
    private String status;

    @Column(length = 60)
    private String referenceType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    protected StoredRecord() {
    }

    public StoredRecord(UUID id, Details details) {
        this.id = id;
        this.type = details.type();
        this.ownerId = details.ownerId();
        this.parentId = details.parentId();
        this.status = details.status();
        this.referenceType = details.referenceType();
        this.payload = details.payload();
        this.createdAt = details.createdAt();
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getStatus() {
        return status;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
