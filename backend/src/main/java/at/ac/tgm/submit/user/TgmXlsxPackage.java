package at.ac.tgm.submit.user;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Provides in-memory access to a small set of XLSX package parts without extracting files. */
final class TgmXlsxPackage {
    private final Map<String, byte[]> entries;

    private TgmXlsxPackage(Map<String, byte[]> entries) {
        this.entries = entries;
    }

    static TgmXlsxPackage read(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    addEntry(entries, entry, zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return new TgmXlsxPackage(entries);
    }

    byte[] required(String name) throws IOException {
        byte[] bytes = entries.get(name);
        if (bytes == null) {
            throw new IOException("XLSX package is missing required part " + name + ".");
        }
        return bytes;
    }

    byte[] optional(String name) {
        return entries.get(name);
    }

    private static void addEntry(Map<String, byte[]> entries, ZipEntry entry, byte[] bytes) throws IOException {
        String name = Path.of(entry.getName()).normalize().toString().replace('\\', '/');
        if (name.startsWith("../") || name.startsWith("/")) {
            throw new IOException("XLSX package contains an invalid part path.");
        }
        entries.put(name, bytes);
    }
}
