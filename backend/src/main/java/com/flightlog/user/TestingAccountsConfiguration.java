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
            StudentClassEntity testingClass = classes.findAll().stream()
                    .filter(entry -> "Testing Class".equals(entry.getName())).findFirst()
                    .orElseGet(() -> classes.save(new StudentClassEntity(UUID.randomUUID(), "Testing Class")));
            ACCOUNTS.stream().filter(account -> users.findByUsername(account.username()).isEmpty())
                    .map(account -> {
                        UserAccount user = new UserAccount(UUID.randomUUID(), account.username(), account.displayName(),
                                encoder.encode(DEFAULT_PASSWORD), account.role());
                        if (account.role() == Role.STUDENT) {
                            user.setStudentClass(testingClass);
                        }
                        return user;
                    }).forEach(users::save);
        };
    }

    private record SeedAccount(String username, String displayName, Role role) {
    }
}
