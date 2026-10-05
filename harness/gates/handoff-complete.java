import java.nio.file.*;

class HandoffComplete {
    static final String[] REQUIRED = {
        "## What was done", "## What was verified", "## What's uncertain"
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: handoff-complete <handoff-note.md>");
            System.exit(2);
        }
        String note = Files.readString(Path.of(args[0]));
        for (String heading : REQUIRED) {
            if (!note.contains(heading)) {
                System.err.println("FAIL: missing heading: " + heading);
                System.exit(1);
            }
        }
        System.out.println("OK: handoff note complete");
    }
}