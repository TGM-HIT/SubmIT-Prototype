package com.flightlog.user;

import com.flightlog.security.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("testing")
public class TestingAccountsConfiguration {
    static final String DEFAULT_PASSWORD = "testing-password";

    private static final List<SeedAccount> ACCOUNTS = List.of(
            new SeedAccount("student01", "Emily Carter", Role.STUDENT),
            new SeedAccount("student02", "Noah Bennett", Role.STUDENT),
            new SeedAccount("student03", "Sofia Ramirez", Role.STUDENT),
            new SeedAccount("student04", "Liam Foster", Role.STUDENT),
            new SeedAccount("student05", "Ava Mitchell", Role.STUDENT),
            new SeedAccount("student06", "Maya Thompson", Role.STUDENT),
            new SeedAccount("student07", "Ethan Brooks", Role.STUDENT),
            new SeedAccount("student08", "Isabella Nguyen", Role.STUDENT),
            new SeedAccount("student09", "Lucas Bennett", Role.STUDENT),
            new SeedAccount("student10", "Amara Johnson", Role.STUDENT),
            new SeedAccount("student11", "Oliver Kim", Role.STUDENT),
            new SeedAccount("student12", "Zara Patel", Role.STUDENT),
            new SeedAccount("student13", "Theo Martinez", Role.STUDENT),
            new SeedAccount("student14", "Nina Wallace", Role.STUDENT),
            new SeedAccount("student15", "Caleb Rivera", Role.STUDENT),
            new SeedAccount("student16", "Riley Stone", Role.STUDENT),
            new SeedAccount("student17", "Jordan Ellis", Role.STUDENT),
            new SeedAccount("student18", "Casey Morgan", Role.STUDENT),
            new SeedAccount("student19", "Taylor Quinn", Role.STUDENT),
            new SeedAccount("student20", "Avery Blake", Role.STUDENT),
            new SeedAccount("student21", "Rowan Hayes", Role.STUDENT),
            new SeedAccount("student22", "Reese Parker", Role.STUDENT),
            new SeedAccount("student23", "Skyler James", Role.STUDENT),
            new SeedAccount("student24", "Dakota Lane", Role.STUDENT),
            new SeedAccount("student25", "Finley Reed", Role.STUDENT),
            new SeedAccount("teacher01", "Olivia Chen", Role.TEACHER),
            new SeedAccount("teacher02", "Marcus Reed", Role.TEACHER),
            new SeedAccount("teacher03", "Priya Shah", Role.TEACHER),
            new SeedAccount("teacher04", "Daniel Brooks", Role.TEACHER),
            new SeedAccount("teacher05", "Elena Torres", Role.TEACHER),
            new SeedAccount("admin01", "Grace Morgan", Role.ADMIN),
            new SeedAccount("admin02", "Samuel Wright", Role.ADMIN));

    @Bean
    @Order(1)
    ApplicationRunner seedTestingAccounts(UserAccountRepository users, StudentClassRepository classes,
            PasswordEncoder encoder) {
        return args -> {
            StudentClassEntity firstClass = testingClass(classes, "Testing Class");
            StudentClassEntity secondClass = testingClass(classes, "Testing Class 2");
            ACCOUNTS.stream().filter(account -> users.findByUsername(account.username()).isEmpty())
                    .map(account -> new UserAccount(UUID.randomUUID(), account.username(), account.displayName(),
                            encoder.encode(DEFAULT_PASSWORD), account.role()))
                    .forEach(users::save);
            for (int index = 0; index < ACCOUNTS.size(); index++) {
                SeedAccount account = ACCOUNTS.get(index);
                if (account.role() == Role.STUDENT) {
                    UserAccount student = users.findByUsername(account.username()).orElseThrow();
                    student.setStudentClass(index % 2 == 0 ? firstClass : secondClass);
                    users.save(student);
                }
            }
        };
    }

    private StudentClassEntity testingClass(StudentClassRepository classes, String name) {
        return classes.findByName(name)
                .orElseGet(() -> classes.save(new StudentClassEntity(UUID.randomUUID(), name)));
    }

    private record SeedAccount(String username, String displayName, Role role) {
    }
}
