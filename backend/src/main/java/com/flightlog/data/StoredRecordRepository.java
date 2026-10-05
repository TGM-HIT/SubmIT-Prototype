package com.flightlog.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredRecordRepository extends JpaRepository<StoredRecord, UUID> {
    Page<StoredRecord> findByType(String type, Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerId(String type, String ownerId, Pageable pageable);

    Page<StoredRecord> findByTypeAndParentId(String type, String parentId, Pageable pageable);

    List<StoredRecord> findByTypeAndParentId(String type, String parentId);

    Optional<StoredRecord> findByTypeAndParentIdAndOwnerId(String type, String parentId, String ownerId);

    Optional<StoredRecord> findFirstByTypeAndParentIdOrderByCreatedAtDesc(String type, String parentId);

    Optional<StoredRecord> findFirstByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(String type, String parentId,
            String ownerId);

    Page<StoredRecord> findByTypeAndReferenceTypeAndParentId(String type, String referenceType, String parentId,
            Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerIdAndParentId(String type, String ownerId, String parentId,
            Pageable pageable);

    Page<StoredRecord> findByTypeAndStatus(String type, String status, Pageable pageable);

    Page<StoredRecord> findByTypeAndOwnerIdAndStatus(String type, String ownerId, String status,
            Pageable pageable);

    Optional<StoredRecord> findByTypeAndOwnerIdAndStatus(String type, String ownerId, String status);
}
