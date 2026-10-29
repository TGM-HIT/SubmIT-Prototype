package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestingTgmMakeTargetTest {
    @Test
    void tgmTargetSelectsDedicatedProfilesAndOverridesConflictingFixtureEnvironment() throws IOException,
            InterruptedException {
        Path root = repositoryRoot();
        ProcessBuilder command = new ProcessBuilder("make", "--no-print-directory", "-n", "run-testing-tgm")
                .directory(root.toFile()).redirectErrorStream(true);
        Map<String, String> environment = command.environment();
        environment.put("DADB_TESTING_MINIMAL_FIXTURES", "false");
        environment.put("DADB_TESTING_ALLOCATION_PIN_PREVIEW", "true");
        environment.put("DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW", "true");
        environment.put("DADB_BOOTSTRAPADMIN_USERNAME", "conflicting-admin");
        environment.put("DADB_BOOTSTRAPADMIN_PASSWORD", "conflicting-password");
        Process process = command.start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("--spring.profiles.active=testing,testing-tgm"), output);
        assertTrue(output.contains("DADB_TESTING_MINIMAL_FIXTURES=true"), output);
        assertTrue(output.contains("DADB_TESTING_ALLOCATION_PIN_PREVIEW=false"), output);
        assertTrue(output.contains("DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=false"), output);
        assertTrue(output.contains("DADB_BOOTSTRAPADMIN_USERNAME="), output);
        assertTrue(output.contains("DADB_BOOTSTRAPADMIN_PASSWORD="), output);
        assertTrue(output.contains("dadb.testing.applying-group-preview=false"), output);
        assertTrue(output.contains("dadb.testing.eligible-formal-project-preview=false"), output);
        assertTrue(output.contains("dadb.testing.formal-proposal-search-preview=false"), output);
        assertTrue(output.contains("dadb.testing.grading-selection-preview=false"), output);
        assertTrue(output.contains("npm ci || npm install"), output);
    }

    @Test
    void ordinaryTestingTargetsRetainTheirFixtureSelectionAndProfile() throws IOException, InterruptedException {
        Path root = repositoryRoot();
        String full = dryRun(root, "run-testing");
        String minimal = dryRun(root, "run-testing-minimal");
        assertTrue(full.contains("env -u DADB_TESTING_MINIMAL_FIXTURES"), full);
        assertTrue(full.contains("--spring.profiles.active=testing"), full);
        assertTrue(minimal.contains("env DADB_TESTING_MINIMAL_FIXTURES=true"), minimal);
        assertTrue(minimal.contains("--spring.profiles.active=testing"), minimal);
        assertTrue(!full.contains("testing-tgm"), full);
        assertTrue(!minimal.contains("testing-tgm"), minimal);
    }

    private String dryRun(Path root, String target) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("make", "--no-print-directory", "-n", target)
                .directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("Makefile"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate the repository Makefile.");
        }
        return current;
    }
}
