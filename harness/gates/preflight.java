import java.io.File;
import java.nio.file.*;

class Preflight {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: preflight <expected-branch>");
            System.exit(2);
        }
        String branch = capture("git", "rev-parse", "--abbrev-ref", "HEAD").trim();
        if (!branch.equals(args[0])) {
            fail("on branch " + branch + ", expected " + args[0]);
        }
        if (!capture("git", "status", "--porcelain").isBlank()) {
            fail("working tree is not clean");
        }
        int javaVersion = Runtime.version().feature();
        if (javaVersion != 23) {
            fail("Java 23 required, found Java " + javaVersion);
        }
        if (!Files.isRegularFile(Path.of("backend", "build.gradle"))) {
            fail("backend/build.gradle not found");
        }
        if (!Files.isRegularFile(Path.of("frontend", "package.json"))) {
            fail("frontend/package.json not found");
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        Path gradleWrapper = Path.of("backend", windows ? "gradlew.bat" : "gradlew");
        if (!Files.isRegularFile(gradleWrapper) || (!windows && !Files.isExecutable(gradleWrapper))) {
            fail("backend Gradle wrapper is missing or not executable");
        }
        if (!onPath("node")) {
            fail("node not found on PATH");
        }
        if (!onPath("npm")) {
            fail("npm not found on PATH");
        }
        System.out.println("OK: preflight clean; backend and frontend toolchains available");
    }

    static void fail(String reason) {
        System.err.println("FAIL: " + reason);
        System.exit(1);
    }

    static String capture(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            fail("command failed: " + String.join(" ", command));
        }
        return out;
    }

    static boolean onPath(String tool) {
        String path = System.getenv("PATH");
        String[] exts = System.getProperty("os.name").toLowerCase().contains("win")
            ? new String[] { ".cmd", ".bat", ".exe", "" }
            : new String[] { "" };
        for (String dir : path.split(File.pathSeparator)) {
            for (String ext : exts) {
                if (Files.isRegularFile(Path.of(dir, tool + ext))) {
                    return true;
                }
            }
        }
        return false;
    }
}