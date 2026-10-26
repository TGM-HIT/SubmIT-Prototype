package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.formal.TestingFormalApplicationFixtureConfiguration;
import ac.at.tgm.submit.security.Role;
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
        "DADB_BOOTSTRAPADMIN_PASSWORD=existing-bootstrap-password",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("default")
class TestingAccountsInactiveProfileTest {
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private StoredRecordRepository records;

    @Test
    void doesNotSeedWorkflowAccountsAndRetainsBootstrapAdminBehaviorWhenTestingProfileIsInactive() {
        assertFalse(applicationContext.containsBean("seedTestingAccounts"));
        assertFalse(applicationContext.containsBean("seedTestingFormalApplicationFixture"));
        assertFalse(applicationContext.containsBean("testingIdeasFixtureSeeder"));
        assertFalse(applicationContext.containsBean("testingAllocationPreviewSeeder"));
        assertTrue(records.findByType("testing_multi_group_allocation_preview",
                org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        assertTrue(records.findByType("idea", org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        assertTrue(records.findByType("application_period", org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        assertTrue(records.findByType("student_application", org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        assertTrue(records.findByType("allocation_run", org.springframework.data.domain.PageRequest.of(0, 20)).isEmpty());
        assertTrue(records.findByTypeAndParentId("project_group",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).isEmpty());
        assertTrue(records.findByTypeAndParentId("formal_application",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).isEmpty());
        assertEquals(1, users.count());
        assertTrue(users.findByUsername("existing-bootstrap-admin").isPresent());
        UserAccount bootstrapAdmin = users.findByUsername("existing-bootstrap-admin").orElseThrow();
        assertEquals(Role.ADMIN, bootstrapAdmin.getRole());
        assertEquals(List.of(Role.ADMIN), bootstrapAdmin.getRoles());
        assertEquals("Administrator", bootstrapAdmin.getDisplayName());
        assertTrue(passwordEncoder.matches("existing-bootstrap-password", bootstrapAdmin.getPasswordHash()));
        List<String> testingUsernames = List.of("student01", "student02", "student03", "student04", "student05",
                "student06", "student07", "student08", "student09", "student10", "student11", "student12",
                "student13", "student14", "student15", "student16", "student17", "student18", "student19",
                "student20", "student21", "student22", "student23", "student24", "student25",
                "teacher01", "teacher02", "teacher03", "teacher04", "teacher05", "admin01", "admin02");
        for (String username : testingUsernames) {
            assertFalse(users.findByUsername(username).isPresent());
        }
    }
}
