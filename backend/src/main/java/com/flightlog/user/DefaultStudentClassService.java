package com.flightlog.user;

import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(0)
public class DefaultStudentClassService implements ApplicationRunner {
    public static final String DEFAULT_CLASS_NAME = "No class / unassigned";

    private final StudentClassRepository classes;

    public DefaultStudentClassService(StudentClassRepository classes) {
        this.classes = classes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        getOrCreate();
    }

    @Transactional
    public StudentClassEntity getOrCreate() {
        return classes.findByName(DEFAULT_CLASS_NAME)
                .orElseGet(() -> classes.save(new StudentClassEntity(UUID.randomUUID(), DEFAULT_CLASS_NAME)));
    }
}
