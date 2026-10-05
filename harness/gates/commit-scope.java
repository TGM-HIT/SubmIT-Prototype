import java.nio.file.*;
import java.util.List;

class CommitScope {

    static final String[] ALLOWED_PREFIXES = { "backend/src/", "frontend/src/" };
    static final String[] ALLOWED_FILES = { "frontend/index.html" };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: commit-scope <changed-paths.txt>");
            System.exit(2);
        }
        List<String> paths = Files.readAllLines(Path.of(args[0]));
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
            if (!allowed) {
                System.err.println("FAIL: out-of-scope path: " + path);
                System.exit(1);
            }
        }
        System.out.println("OK: commit scope clean");
    }
}