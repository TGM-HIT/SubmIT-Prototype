package com.flightlog.user;

import com.flightlog.security.Role;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Moves legacy classless student rows out of the active student role without inventing assignments. */
@Component
public class StudentAccountLifecycle implements ApplicationRunner {
    private final UserAccountRepository users;

    public StudentAccountLifecycle(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findAllByRoleForUpdate(Role.STUDENT).stream()
                .filter(user -> user.getStudentClass() == null)
                .forEach(user -> user.setRole(Role.PENDING_STUDENT));
    }
}
