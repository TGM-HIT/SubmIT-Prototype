package at.ac.tgm.submit.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredRecordRepository extends JpaRepository<StoredRecord, UUID> {
    Page<StoredRecord> findByType(String type, Pageable pageable);

    long countByType(String type);

    Page<StoredRecord> findByTypeOrderByIdAsc(String type, Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerId(String type, String ownerId, Pageable pageable);

    @Query(value = "select record from StoredRecord record where record.type = 'idea' "
            + "and (record.ownerId = :callerId or record.collaborator1Id = :callerId "
            + "or record.collaborator2Id = :callerId or record.collaborator3Id = :callerId "
            + "or record.collaborator4Id = :callerId) "
            + "and (:ownerId is null or record.ownerId = :ownerId or record.collaborator1Id = :ownerId "
            + "or record.collaborator2Id = :ownerId or record.collaborator3Id = :ownerId "
            + "or record.collaborator4Id = :ownerId) "
            + "and (:status is null or record.status = :status) "
            + "and (:schoolYear is null or record.ideaSchoolYear = :schoolYear)",
            countQuery = "select count(record) from StoredRecord record where record.type = 'idea' "
                    + "and (record.ownerId = :callerId or record.collaborator1Id = :callerId "
                    + "or record.collaborator2Id = :callerId or record.collaborator3Id = :callerId "
                    + "or record.collaborator4Id = :callerId) "
                    + "and (:ownerId is null or record.ownerId = :ownerId or record.collaborator1Id = :ownerId "
                    + "or record.collaborator2Id = :ownerId or record.collaborator3Id = :ownerId "
                    + "or record.collaborator4Id = :ownerId) "
                    + "and (:status is null or record.status = :status) "
                    + "and (:schoolYear is null or record.ideaSchoolYear = :schoolYear)")
    Page<StoredRecord> findStudentIdeas(@Param("callerId") String callerId, @Param("ownerId") String ownerId,
            @Param("status") String status, @Param("schoolYear") String schoolYear, Pageable pageable);

    Page<StoredRecord> findByTypeAndParentId(String type, String parentId, Pageable pageable);

    List<StoredRecord> findByTypeAndParentId(String type, String parentId);

    @Query("select record from StoredRecord record where record.type = :type and record.parentId = :parentId "
            + "and (record.createdAt > :createdAt or (record.createdAt = :createdAt and record.id > :id))")
    Page<StoredRecord> findByTypeAndParentIdAfterCreationCursor(@Param("type") String type,
            @Param("parentId") String parentId, @Param("createdAt") Instant createdAt, @Param("id") UUID id,
            Pageable pageable);

    List<StoredRecord> findByTypeAndParentIdOrderByCreatedAtDesc(String type, String parentId);

    List<StoredRecord> findByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(String type, String parentId,
            String ownerId);

    Optional<StoredRecord> findByTypeAndParentIdAndOwnerId(String type, String parentId, String ownerId);

    Optional<StoredRecord> findFirstByTypeAndParentIdOrderByCreatedAtDesc(String type, String parentId);

    Optional<StoredRecord> findFirstByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(String type, String parentId,
            String ownerId);

    Page<StoredRecord> findByTypeAndReferenceTypeAndParentId(String type, String referenceType, String parentId,
            Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerIdAndParentId(String type, String ownerId, String parentId,
            Pageable pageable);

    Page<StoredRecord> findByTypeAndStatus(String type, String status, Pageable pageable);

    Page<StoredRecord> findByTypeAndStatusAndIdGreaterThan(String type, String status, UUID id, Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerIdAndStatus(String type, String ownerId, String status,
            Pageable pageable);

    Optional<StoredRecord> findByTypeAndOwnerIdAndStatus(String type, String ownerId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select record from StoredRecord record where record.id = :id")
    Optional<StoredRecord> findByIdForUpdate(@Param("id") UUID id);
}
