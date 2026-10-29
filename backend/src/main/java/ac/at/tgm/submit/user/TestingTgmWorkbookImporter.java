package ac.at.tgm.submit.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

/** Imports the local workbook only in the explicitly selected disposable testing profile. */
@Component
@Profile("testing-tgm")
@Order(3)
public class TestingTgmWorkbookImporter implements ApplicationRunner {
    private static final String WORKBOOK_RELATIVE_PATH = "docs/Projekte_2026_27.xlsx";

    private final Environment environment;
    private final TgmWorkbookReader reader;
    private final TgmWorkbookImportPlanner planner;
    private final TgmWorkbookImportWriter writer;

    public TestingTgmWorkbookImporter(Environment environment, TgmWorkbookReader reader,
            TgmWorkbookImportPlanner planner, TgmWorkbookImportWriter writer) {
        this.environment = environment;
        this.reader = reader;
        this.planner = planner;
        this.writer = writer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        requireSupportProfile();
        Path source = workbookPath();
        importSource(source, readSource(source));
    }

    void importSource(Path source, byte[] bytes) {
        try {
            String checksum = sha256(bytes);
            if (writer.alreadyImported(checksum)) {
                return;
            }
            TgmWorkbookReader.WorkbookData workbook = reader.read(source, bytes);
            TgmImportPlan plan = planner.plan(source, workbook);
            writer.write(checksum, workbook, plan);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void requireSupportProfile() {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("testing"))) {
            throw new IllegalStateException("The testing-tgm profile requires the testing support profile.");
        }
    }

    private Path workbookPath() {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            Path candidate = directory.resolve(WORKBOOK_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Required local TGM workbook is missing at " + WORKBOOK_RELATIVE_PATH
                + " (resolved from the application working directory).");
    }

    byte[] readSource(Path source) {
        try {
            return Files.readAllBytes(source);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read required local TGM workbook at " + source + ".", exception);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
