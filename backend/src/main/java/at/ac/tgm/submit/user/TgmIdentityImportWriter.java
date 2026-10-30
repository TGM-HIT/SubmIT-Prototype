package at.ac.tgm.submit.user;

import at.ac.tgm.submit.security.Role;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Persists the planned imported student accounts and their four source classes. */
@Component
class TgmIdentityImportWriter {
    private final UserAccountRepository users;
    private final StudentClassRepository classes;
    private final PasswordEncoder passwords;

    TgmIdentityImportWriter(UserAccountRepository users, StudentClassRepository classes, PasswordEncoder passwords) {
        this.users = users;
        this.classes = classes;
        this.passwords = passwords;
    }

    ImportedIdentities write(TgmImportPlan plan) {
        Map<String, StudentClassEntity> classMap = importClasses();
        Map<String, UserAccount> students = importStudents(plan, classMap);
        Map<String, UserAccount> staff = Map.of(
                "teacher01", users.findByUsername("teacher01").orElseThrow(),
                "teacher02", users.findByUsername("teacher02").orElseThrow(),
                "admin01", users.findByUsername("admin01").orElseThrow());
        return new ImportedIdentities(classMap, students, staff);
    }

    private Map<String, StudentClassEntity> importClasses() {
        Map<String, StudentClassEntity> result = new LinkedHashMap<>();
        for (String sheet : TgmWorkbookReader.SHEETS) {
            StudentClassEntity entity = classes.findByName(sheet)
                    .orElseGet(() -> classes.save(new StudentClassEntity(UUID.randomUUID(), sheet)));
            result.put(sheet, entity);
        }
        return result;
    }

    private Map<String, UserAccount> importStudents(TgmImportPlan plan,
            Map<String, StudentClassEntity> classMap) {
        Map<String, UserAccount> result = new LinkedHashMap<>();
        for (TgmWorkbookReader.StudentRow source : plan.students()) {
            UserAccount account = new UserAccount(UUID.randomUUID(), source.username(), source.displayName(),
                    passwords.encode(TestingAccountsConfiguration.DEFAULT_PASSWORD), Role.STUDENT);
            account.setStudentClass(classMap.get(source.sheet()));
            result.put(coordinate(source), users.save(account));
        }
        return result;
    }

    private String coordinate(TgmWorkbookReader.StudentRow row) {
        return row.sheet() + "!" + row.row();
    }

    record ImportedIdentities(Map<String, StudentClassEntity> classes, Map<String, UserAccount> students,
            Map<String, UserAccount> staff) {
    }
}
