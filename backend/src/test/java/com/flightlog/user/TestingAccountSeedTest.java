package com.flightlog.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.flightlog.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("testing")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingaccountseed;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class TestingAccountSeedTest {
    @Autowired private UserAccountRepository users;

    @Test
    @Transactional
    void seededActiveStudentsHaveTheirExplicitTestingClass() {
        var students = users.findAll().stream().filter(account -> account.getRole() == Role.STUDENT)
                .filter(account -> account.getUsername().startsWith("student")).toList();
        assertFalse(students.isEmpty());
        assertEquals(15, students.size());
        students.forEach(student -> assertEquals("Testing Class", student.getStudentClass().getName()));
    }
}
