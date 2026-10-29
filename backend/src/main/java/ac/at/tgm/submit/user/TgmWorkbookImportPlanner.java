package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.Role;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Performs all source/account/group checks before any imported record is written. */
@Component
class TgmWorkbookImportPlanner {
    private static final String GROUP_PREFIX = "group:";
    private static final int MAX_STUDENT_LOGIN_LENGTH = 80;
    private static final int MAX_STUDENT_NAME_LENGTH = 120;
    private static final int MAX_PROJECT_TITLE_LENGTH = 200;
    private static final int MAX_GROUP_MEMBERS = 5;
    private static final int ORDINARY_TEST_STUDENT_COUNT = 25;

    private final Environment environment;
    private final UserAccountRepository users;
    private final StudentClassRepository classes;

    TgmWorkbookImportPlanner(Environment environment, UserAccountRepository users, StudentClassRepository classes) {
        this.environment = environment;
        this.users = users;
        this.classes = classes;
    }

    TgmImportPlan plan(Path source, TgmWorkbookReader.WorkbookData workbook) {
        validateStudentRows(source, workbook.students());
        validateExistingAccounts(source, workbook.students());
        validateExistingClasses(source);
        Map<String, Boolean> rankings = rankingValidity(source, workbook.students());
        Map<String, String> skipped = new LinkedHashMap<>();
        rankings.forEach((key, valid) -> {
            if (!valid) {
                skipped.put(key, rankingReason(findStudent(workbook.students(), key)));
            }
        });
        List<TgmGroupPlan> groups = qualifyingGroups(workbook.students(), rankings, skipped);
        Set<String> groupedStudents = groups.stream().flatMap(group -> group.members().stream())
                .map(TgmWorkbookImportPlanner::coordinate).collect(Collectors.toSet());
        List<TgmWorkbookReader.StudentRow> solos = workbook.students().stream()
                .filter(student -> Boolean.TRUE.equals(rankings.get(coordinate(student))))
                .filter(student -> !groupedStudents.contains(coordinate(student))).toList();
        return new TgmImportPlan(List.copyOf(workbook.students()), List.copyOf(workbook.projectTitles()),
                Map.copyOf(rankings), Map.copyOf(skipped), groups, solos);
    }

    private void validateStudentRows(Path source, List<TgmWorkbookReader.StudentRow> students) {
        if (students.isEmpty()) {
            throw new IllegalStateException("TGM workbook " + source + " has no student rows in the required sheets.");
        }
        Map<String, TgmWorkbookReader.StudentRow> usernames = new HashMap<>();
        Map<String, TgmWorkbookReader.StudentRow> identities = new HashMap<>();
        for (TgmWorkbookReader.StudentRow student : students) {
            validateStudentIdentity(source, student, usernames, identities);
            validateProjectTitles(source, student);
        }
    }

    private void validateStudentIdentity(Path source, TgmWorkbookReader.StudentRow student,
            Map<String, TgmWorkbookReader.StudentRow> usernames,
            Map<String, TgmWorkbookReader.StudentRow> identities) {
        if (usernames.putIfAbsent(student.username(), student) != null) {
            throw sourceError(source, student, "login collides after whitespace removal");
        }
        String identity = normalizedDisplayName(student.displayName());
        if (identities.putIfAbsent(identity, student) != null) {
            throw sourceError(source, student, "student identity is ambiguous");
        }
        if (student.username().length() < 3 || student.username().length() > MAX_STUDENT_LOGIN_LENGTH
                || student.displayName().length() > MAX_STUDENT_NAME_LENGTH) {
            throw sourceError(source, student, "student identity exceeds account field constraints");
        }
    }

    private void validateProjectTitles(Path source, TgmWorkbookReader.StudentRow student) {
        for (String title : student.choices()) {
            if (!title.isBlank() && title.length() > MAX_PROJECT_TITLE_LENGTH) {
                throw sourceError(source, student, "project title exceeds the accepted idea title limit");
            }
        }
    }

    private void validateExistingAccounts(Path source, List<TgmWorkbookReader.StudentRow> students) {
        List<UserAccount> existingAccounts = users.findAll();
        validateTestingStaffAccounts();
        Set<String> usernames = existingAccounts.stream().map(UserAccount::getUsername).collect(Collectors.toSet());
        Set<String> identities = existingAccounts.stream().map(UserAccount::getDisplayName)
                .map(TgmWorkbookImportPlanner::normalizedDisplayName).collect(Collectors.toSet());
        rejectOrdinaryStudentsIfPresent();
        for (TgmWorkbookReader.StudentRow student : students) {
            if (usernames.contains(student.username())) {
                throw sourceError(source, student, "login conflicts with an account not owned by this import");
            }
            if (identities.contains(normalizedDisplayName(student.displayName()))) {
                throw sourceError(source, student, "display identity conflicts with an existing account");
            }
        }
    }

    private void validateTestingStaffAccounts() {
        requireStaffAccount("teacher01", Role.TEACHER);
        requireStaffAccount("teacher02", Role.TEACHER);
        requireStaffAccount("admin01", Role.ADMIN);
    }

    private void requireStaffAccount(String username, Role role) {
        UserAccount staff = users.findByUsername(username).orElse(null);
        if (staff == null || !staff.hasRole(role)) {
            throw new IllegalStateException("Testing support account " + username + " is unavailable or has the wrong role.");
        }
    }

    private void rejectOrdinaryStudentsIfPresent() {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("testing-tgm"))) {
            return;
        }
        for (int number = 1; number <= ORDINARY_TEST_STUDENT_COUNT; number++) {
            String username = "student%02d".formatted(number);
            if (users.findByUsername(username).isPresent()) {
                throw new IllegalStateException("Dedicated TGM profile conflicts with an existing ordinary testing "
                        + "student account (" + username + "). Use a fresh disposable database.");
            }
        }
    }

    private void validateExistingClasses(Path source) {
        List<UserAccount> existingStudents = users.findAll().stream()
                .filter(user -> user.hasRole(Role.STUDENT)).toList();
        for (String className : TgmWorkbookReader.SHEETS) {
            if (classes.findByName(className).isEmpty()) {
                continue;
            }
            boolean classHasStudents = existingStudents.stream().anyMatch(student ->
                    student.getStudentClass() != null && className.equals(student.getStudentClass().getName()));
            if (classHasStudents) {
                throw new IllegalStateException("TGM workbook " + source + ", sheet " + className
                        + ", row 1: the class is already assigned to unrelated student accounts.");
            }
        }
    }

    private Map<String, Boolean> rankingValidity(Path source, List<TgmWorkbookReader.StudentRow> students) {
        Map<String, Boolean> valid = new LinkedHashMap<>();
        for (TgmWorkbookReader.StudentRow student : students) {
            List<String> choices = student.choices();
            if (choices.stream().anyMatch(String::isBlank)) {
                valid.put(coordinate(student), false);
            } else if (new HashSet<>(choices).size() != 3) {
                valid.put(coordinate(student), false);
            } else {
                valid.put(coordinate(student), true);
            }
        }
        return valid;
    }

    private String rankingReason(TgmWorkbookReader.StudentRow student) {
        if (student.choices().stream().anyMatch(String::isBlank)) {
            return "fewer than three nonblank preferences";
        }
        return "repeated preference";
    }

    private List<TgmGroupPlan> qualifyingGroups(List<TgmWorkbookReader.StudentRow> students,
            Map<String, Boolean> rankings, Map<String, String> skipped) {
        Map<String, TgmWorkbookReader.StudentRow> names = new HashMap<>();
        students.forEach(student -> names.put(normalizedDisplayName(student.displayName()), student));
        Map<String, Declaration> declarations = declarations(students, names, skipped);
        Map<String, Candidate> candidates = candidates(declarations);
        List<Candidate> valid = validateCandidates(candidates, declarations, rankings, skipped);
        Set<String> overlaps = overlappingGroups(valid);
        recordOverlaps(valid, overlaps, skipped);
        return valid.stream().filter(candidate -> !overlaps.contains(candidate.key()))
                .map(Candidate::toPlan).toList();
    }

    private Map<String, Declaration> declarations(List<TgmWorkbookReader.StudentRow> students,
            Map<String, TgmWorkbookReader.StudentRow> names, Map<String, String> skipped) {
        Map<String, Declaration> declarations = new LinkedHashMap<>();
        for (TgmWorkbookReader.StudentRow student : students) {
            if (!student.declaredMembers().isEmpty()) {
                resolveDeclaration(student, students, names, declarations, skipped);
            }
        }
        return declarations;
    }

    private void resolveDeclaration(TgmWorkbookReader.StudentRow student,
            List<TgmWorkbookReader.StudentRow> students, Map<String, TgmWorkbookReader.StudentRow> names,
            Map<String, Declaration> declarations, Map<String, String> skipped) {
        RosterResolution resolution = resolveRoster(student, names);
        if (resolution.failure() != null) {
            skipped.put(GROUP_PREFIX + coordinate(student), resolution.failure());
            return;
        }
        List<TgmWorkbookReader.StudentRow> members = resolution.members();
        members.sort(Comparator.comparingInt(students::indexOf));
        List<String> orderedKeys = members.stream().map(TgmWorkbookImportPlanner::coordinate).toList();
        String groupKey = String.join("|", orderedKeys);
        declarations.put(coordinate(student), new Declaration(orderedKeys, groupKey, members));
    }

    private RosterResolution resolveRoster(TgmWorkbookReader.StudentRow student,
            Map<String, TgmWorkbookReader.StudentRow> names) {
        List<TgmWorkbookReader.StudentRow> members = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        String failure = rosterSizeError(student);
        if (failure != null) {
            return new RosterResolution(List.of(), failure);
        }
        for (String declared : student.declaredMembers()) {
            TgmWorkbookReader.StudentRow member = names.get(normalizedDisplayName(declared));
            if (member == null) {
                return new RosterResolution(List.of(), "declared member does not resolve uniquely");
            }
            if (!addMember(member, members, keys)) {
                return new RosterResolution(List.of(), "duplicate declared member");
            }
        }
        String studentKey = coordinate(student);
        if (!keys.contains(studentKey)) {
            return new RosterResolution(List.of(), "declaration omits its student");
        }
        return new RosterResolution(members, null);
    }

    private String rosterSizeError(TgmWorkbookReader.StudentRow student) {
        int size = student.declaredMembers().size();
        return size < 2 || size > MAX_GROUP_MEMBERS ? "declared roster size is outside 2–5" : null;
    }

    private boolean addMember(TgmWorkbookReader.StudentRow member, List<TgmWorkbookReader.StudentRow> members,
            Set<String> keys) {
        String key = coordinate(member);
        if (!keys.add(key)) {
            return false;
        }
        members.add(member);
        return true;
    }

    private Map<String, Candidate> candidates(Map<String, Declaration> declarations) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        declarations.values().forEach(declaration ->
                candidates.putIfAbsent(declaration.groupKey(), new Candidate(declaration.memberKeys(),
                        declaration.members())));
        return candidates;
    }

    private List<Candidate> validateCandidates(Map<String, Candidate> candidates,
            Map<String, Declaration> declarations, Map<String, Boolean> rankings, Map<String, String> skipped) {
        List<Candidate> valid = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            String failure = candidateFailure(candidate, declarations, rankings);
            if (failure == null) {
                valid.add(candidate);
            } else {
                String reason = failure;
                candidate.members().forEach(member ->
                        skipped.putIfAbsent(GROUP_PREFIX + coordinate(member), reason));
            }
        }
        return valid;
    }

    private String candidateFailure(Candidate candidate, Map<String, Declaration> declarations,
            Map<String, Boolean> rankings) {
        List<String> expected = null;
        for (TgmWorkbookReader.StudentRow member : candidate.members()) {
            String key = coordinate(member);
            Declaration declaration = declarations.get(key);
            if (declaration == null || !declaration.groupKey().equals(candidate.key())) {
                return "roster is not reciprocal";
            }
            if (!Boolean.TRUE.equals(rankings.get(key))) {
                return "a member does not have three distinct valid preferences";
            }
            List<String> ranking = member.choices();
            if (expected != null && !expected.equals(ranking)) {
                return "member preferences differ or are reordered";
            }
            expected = ranking;
        }
        return null;
    }

    private Set<String> overlappingGroups(List<Candidate> candidates) {
        Set<String> overlaps = new HashSet<>();
        for (int left = 0; left < candidates.size(); left++) {
            for (int right = left + 1; right < candidates.size(); right++) {
                Set<String> intersection = new HashSet<>(candidates.get(left).memberKeys());
                intersection.retainAll(candidates.get(right).memberKeys());
                if (!intersection.isEmpty()) {
                    overlaps.add(candidates.get(left).key());
                    overlaps.add(candidates.get(right).key());
                }
            }
        }
        return overlaps;
    }

    private void recordOverlaps(List<Candidate> valid, Set<String> overlaps, Map<String, String> skipped) {
        valid.stream().filter(candidate -> overlaps.contains(candidate.key())).forEach(candidate ->
                candidate.members().forEach(member ->
                        skipped.putIfAbsent(GROUP_PREFIX + coordinate(member),
                                "student is declared in overlapping groups")));
    }

    private TgmWorkbookReader.StudentRow findStudent(List<TgmWorkbookReader.StudentRow> students, String coordinate) {
        return students.stream().filter(student -> coordinate(student).equals(coordinate)).findFirst().orElseThrow();
    }

    private static String normalizedDisplayName(String value) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int codePoint : value.codePoints().toArray()) {
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }
        return normalized.toString();
    }

    private static String coordinate(TgmWorkbookReader.StudentRow row) {
        return row.sheet() + "!" + row.row();
    }

    private IllegalStateException sourceError(Path path, TgmWorkbookReader.StudentRow row, String reason) {
        return new IllegalStateException("TGM workbook " + path + ", sheet " + row.sheet() + ", row " + row.row()
                + ": " + reason + ".");
    }

    private record Declaration(List<String> memberKeys, String groupKey,
            List<TgmWorkbookReader.StudentRow> members) {
    }

    private record Candidate(List<String> memberKeys, List<TgmWorkbookReader.StudentRow> members) {
        String key() {
            return String.join("|", memberKeys);
        }

        TgmGroupPlan toPlan() {
            return new TgmGroupPlan(members, members.getFirst().choices());
        }
    }

    private record RosterResolution(List<TgmWorkbookReader.StudentRow> members, String failure) {
    }
}
