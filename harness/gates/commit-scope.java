import java.nio.file.*;
import java.util.List;

class CommitScope {

    static final String[] ALLOWED_PREFIXES = {
        "backend/src/",
        "frontend/src/",
        "frontend/scripts/"
    };
    static final String[] ALLOWED_FILES = {
        ".gitignore",
        ".env.example",
        "Makefile",
        "frontend/index.html",
        "frontend/package.json",
        "frontend/package-lock.json",
        "frontend/Dockerfile",
        "frontend/DEV-GUIDELINES.md",
        "backend/DEV-GUIDELINES.md",
        "frontend/public/favicon.ico",
        "harness/gates/commit-scope.java",
        "harness/checks/Compliance-04.md",
        "harness/roles/auditor.md",
        "harness/roles/implementor.md",
        "harness/roles/learner.md",
        "harness/roles/orchestrator.md",
        "harness/tickets/done/Bug-04.md",
        "harness/tickets/done/Bug-05.md",
        "harness/tickets/done/Feature-01.md",
        "harness/tickets/done/Feature-02.md",
        "harness/tickets/done/Feature-04.md",
        "harness/tickets/done/Feature-05.md",
        "harness/tickets/done/Feature-07.md",
        "harness/tickets/done/Feature-08.md"
    };
    static final String[] SPEC_FILES = { "docs/SPEC.md", "backend/SPEC.md", "frontend/SPEC.md" };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: commit-scope <changed-paths.txt>");
            System.exit(2);
        }
        List<String> paths = Files.readAllLines(Path.of(args[0]));
        List<String> specChanges = new java.util.ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            if (i == 0 && path.startsWith("\uFEFF")) {
                path = path.substring(1);
            }
            if (path.isBlank()) {
                continue;
            }
            boolean allowed = false;
            for (String prefix : ALLOWED_PREFIXES) {
                if (path.startsWith(prefix)) {
                    allowed = true;
                    break;
                }
            }
            for (String file : ALLOWED_FILES) {
                if (path.equals(file)) {
                    allowed = true;
                    break;
                }
            }
            for (String file : SPEC_FILES) {
                if (path.equals(file)) {
                    allowed = true;
                    specChanges.add(path);
                    break;
                }
            }
            if (!allowed) {
                System.err.println("FAIL: out-of-scope path: " + path);
                System.exit(1);
            }
        }
        for (String spec : specChanges) {
            System.out.println("FLAG: spec change: " + spec);
        }
        System.out.println("OK: commit scope clean");
    }
}