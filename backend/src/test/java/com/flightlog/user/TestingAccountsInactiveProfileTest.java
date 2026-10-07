package com.flightlog.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flightlog.security.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingaccountsinactive;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_BOOTSTRAPADMIN_USERNAME=existing-bootstrap-admin",
        "DADB_BOOTSTRAPADMIN_PASSWORD=existing-bootstrap-password"
})
@ActiveProfiles("default")
class TestingAccountsInactiveProfileTest {
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void doesNotSeedWorkflowAccountsAndRetainsBootstrapAdminBehaviorWhenTestingProfileIsInactive() {
        assertFalse(applicationContext.containsBean("seedTestingAccounts"));
        assertEquals(1, users.count());
        assertTrue(users.findByUsername("existing-bootstrap-admin").isPresent());
        UserAccount bootstrapAdmin = users.findByUsername("existing-bootstrap-admin").orElseThrow();
        assertEquals(Role.ADMIN, bootstrapAdmin.getRole());
        assertTrue(passwordEncoder.matches("existing-bootstrap-password", bootstrapAdmin.getPasswordHash()));
        List<String> testingUsernames = List.of("student01", "student02", "student03", "student04", "student05",
                "teacher01", "teacher02", "teacher03", "teacher04", "teacher05");
        for (String username : testingUsernames) {
            assertFalse(users.findByUsername(username).isPresent());
        }
    }
}
