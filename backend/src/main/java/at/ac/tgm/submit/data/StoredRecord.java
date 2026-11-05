package at.ac.tgm.submit.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "domain_records", indexes = {
        @Index(name = "idx_domain_idea_creator", columnList = "type, owner_id, status, idea_school_year"),
        @Index(name = "idx_domain_idea_collaborator_1",
                columnList = "type, collaborator_1_id, status, idea_school_year"),
        @Index(name = "idx_domain_idea_collaborator_2",
                columnList = "type, collaborator_2_id, status, idea_school_year"),
        @Index(name = "idx_domain_idea_collaborator_3",
                columnList = "type, collaborator_3_id, status, idea_school_year"),
        @Index(name = "idx_domain_idea_collaborator_4",
                columnList = "type, collaborator_4_id, status, idea_school_year")
})
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

    @Column(name = "collaborator_1_id", length = 36)
    private String collaborator1Id;

    @Column(name = "collaborator_2_id", length = 36)
    private String collaborator2Id;

    @Column(name = "collaborator_3_id", length = 36)
    private String collaborator3Id;

    @Column(name = "collaborator_4_id", length = 36)
    private String collaborator4Id;

    @Column(name = "idea_school_year", length = 40)
    private String ideaSchoolYear;

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

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getStatus() {
        return status;
    }

    public String getCollaborator1Id() {
        return collaborator1Id;
    }

    public String getCollaborator2Id() {
        return collaborator2Id;
    }

    public String getCollaborator3Id() {
        return collaborator3Id;
    }

    public String getCollaborator4Id() {
        return collaborator4Id;
    }

    public String getIdeaSchoolYear() {
        return ideaSchoolYear;
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

    public void setIdeaListIndex(String collaborator1Id, String collaborator2Id, String collaborator3Id,
            String collaborator4Id, String ideaSchoolYear) {
        this.collaborator1Id = collaborator1Id;
        this.collaborator2Id = collaborator2Id;
        this.collaborator3Id = collaborator3Id;
        this.collaborator4Id = collaborator4Id;
        this.ideaSchoolYear = ideaSchoolYear;
    }
}
