package com.flightlog.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentClassRepository extends JpaRepository<StudentClassEntity, UUID> {
}
