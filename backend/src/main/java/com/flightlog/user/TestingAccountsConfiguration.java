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
            new SeedAccount("student01", "Student 01", Role.STUDENT),
            new SeedAccount("student02", "Student 02", Role.STUDENT),
            new SeedAccount("student03", "Student 03", Role.STUDENT),
            new SeedAccount("student04", "Student 04", Role.STUDENT),
            new SeedAccount("student05", "Student 05", Role.STUDENT),
            new SeedAccount("teacher01", "Teacher 01", Role.TEACHER),
            new SeedAccount("teacher02", "Teacher 02", Role.TEACHER),
            new SeedAccount("teacher03", "Teacher 03", Role.TEACHER),
            new SeedAccount("teacher04", "Teacher 04", Role.TEACHER),
            new SeedAccount("teacher05", "Teacher 05", Role.TEACHER));

    @Bean
    @Order(1)
    ApplicationRunner seedTestingAccounts(UserAccountRepository users, PasswordEncoder encoder) {
        return args -> ACCOUNTS.stream()
                .filter(account -> users.findByUsername(account.username()).isEmpty())
                .map(account -> new UserAccount(UUID.randomUUID(), account.username(), account.displayName(),
                        encoder.encode(DEFAULT_PASSWORD), account.role()))
                .forEach(users::save);
    }

    private record SeedAccount(String username, String displayName, Role role) {
    }
}
