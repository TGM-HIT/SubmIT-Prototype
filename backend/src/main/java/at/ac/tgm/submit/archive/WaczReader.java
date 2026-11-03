package at.ac.tgm.submit.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/** Reads the bounded WACZ profile supported by the public snapshot viewer. */
@Component
public class WaczReader {
    private static final int MAX_UNZIPPED_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RESOURCES = 2000;
    private static final int HEADER_DELIMITER_LENGTH = 4;
    private static final int COPY_BUFFER_SIZE = 8192;
    private static final String MANIFEST = "datapackage.json";
    private static final String INDEX = "indexes/index.cdxj";
    private final ObjectMapper objectMapper;

    public WaczReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Snapshot read(byte[] content) {
        Map<String, byte[]> entries = readEntries(content);
        validateManifest(entries.get(MANIFEST));
        validateIndex(entries.get(INDEX));
        List<Resource> resources = readResources(entries);
        validateResources(resources);
        return new Snapshot(resources);
    }

    private Map<String, byte[]> readEntries(byte[] content) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int totalBytes = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] bytes = readLimited(zip, MAX_UNZIPPED_BYTES - totalBytes);
                    totalBytes += bytes.length;
                    if (totalBytes > MAX_UNZIPPED_BYTES) {
                        throw invalid();
                    }
                    entries.put(entry.getName(), bytes);
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw invalid();
        }
        return entries;
    }

    private void validateManifest(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw invalid();
        }
        try {
            JsonNode manifest = objectMapper.readTree(bytes);
            if (!manifest.isObject() || !manifest.path("wacz_version").isTextual()
                    || !manifest.path("resources").isArray()) {
                throw invalid();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private void validateIndex(byte[] index) {
        if (index == null || index.length == 0) {
            throw invalid();
        }
    }

    private List<Resource> readResources(Map<String, byte[]> entries) {
        List<Resource> resources = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (isWarc(entry.getKey())) {
                resources.addAll(readWarc(entry.getValue()));
            }
        }
        return resources;
    }

    private boolean isWarc(String path) {
        return path.startsWith("archive/") && path.endsWith(".warc.gz");
    }

    private void validateResources(List<Resource> resources) {
        if (resources.isEmpty() || resources.size() > MAX_RESOURCES
                || resources.stream().noneMatch(resource -> resource.mimeType().startsWith("text/html"))) {
            throw invalid();
        }
    }

    private List<Resource> readWarc(byte[] compressed) {
        byte[] warc = decompress(compressed);
        List<Resource> resources = new ArrayList<>();
        int offset = 0;
        while (offset < warc.length) {
            WarcRecord record = recordAt(warc, offset);
            if (record == null) {
                break;
            }
            addResponse(resources, record);
            offset = nextRecordOffset(warc, record);
            if (resources.size() > MAX_RESOURCES) {
                throw invalid();
            }
        }
        return resources;
    }

    private byte[] decompress(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return readLimited(gzip, MAX_UNZIPPED_BYTES);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private WarcRecord recordAt(byte[] warc, int offset) {
        int headerEnd = delimiter(warc, offset);
        if (headerEnd < 0) {
            return null;
        }
        String header = new String(warc, offset, headerEnd - offset, StandardCharsets.ISO_8859_1);
        int recordLength = contentLength(header);
        int recordStart = headerEnd + HEADER_DELIMITER_LENGTH;
        if (recordLength < 0 || recordStart + recordLength > warc.length) {
            throw invalid();
        }
        byte[] record = java.util.Arrays.copyOfRange(warc, recordStart, recordStart + recordLength);
        return new WarcRecord(header, recordStart, recordLength, record);
    }

    private void addResponse(List<Resource> resources, WarcRecord record) {
        if (!isResponse(record.header())) {
            return;
        }
        String target = headerValue(record.header(), "WARC-Target-URI");
        Resource resource = response(target, record.content());
        if (resource != null) {
            resources.add(resource);
        }
    }

    private boolean isResponse(String header) {
        return header.startsWith("WARC/") && header.toLowerCase(Locale.ROOT).contains("warc-type: response");
    }

    private int nextRecordOffset(byte[] warc, WarcRecord record) {
        int offset = record.start() + record.length();
        while (offset < warc.length && (warc[offset] == '\r' || warc[offset] == '\n')) {
            offset++;
        }
        return offset;
    }

    private Resource response(String target, byte[] record) {
        int responseEnd = delimiter(record, 0);
        if (responseEnd < 0 || target == null || target.isBlank()) {
            return null;
        }
        String responseHeaders = new String(record, 0, responseEnd, StandardCharsets.ISO_8859_1);
        if (!responseHeaders.startsWith("HTTP/")) {
            return null;
        }
        String mimeType = headerValue(responseHeaders, "Content-Type");
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        int bodyStart = responseEnd + HEADER_DELIMITER_LENGTH;
        byte[] body = java.util.Arrays.copyOfRange(record, bodyStart, record.length);
        return new Resource(target, mimeType, Base64.getEncoder().encodeToString(body));
    }

    private int delimiter(byte[] bytes, int start) {
        for (int index = start; index + 3 < bytes.length; index++) {
            if (bytes[index] == '\r' && bytes[index + 1] == '\n'
                    && bytes[index + 2] == '\r' && bytes[index + 3] == '\n') {
                return index;
            }
        }
        return -1;
    }

    private int contentLength(String headers) {
        String value = headerValue(headers, "Content-Length");
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private String headerValue(String headers, String name) {
        return headers.lines().filter(line -> line.regionMatches(true, 0, name + ":", 0, name.length() + 1))
                .map(line -> line.substring(name.length() + 1).trim()).findFirst().orElse(null);
    }

    private byte[] readLimited(java.io.InputStream input, int limit) throws IOException {
        if (limit < 0) {
            throw invalid();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) {
                throw invalid();
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("This approved website snapshot is malformed or uses an unsupported WACZ profile.");
    }

    private record WarcRecord(String header, int start, int length, byte[] content) {
    }

    public record Snapshot(List<Resource> resources) {
    }

    public record Resource(String url, String mimeType, String bodyBase64) {
    }
}
