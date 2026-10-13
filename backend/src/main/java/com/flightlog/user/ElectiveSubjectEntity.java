package com.flightlog.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "elective_subjects")
public class ElectiveSubjectEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected ElectiveSubjectEntity() {
    }

    public ElectiveSubjectEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
