package at.ac.tgm.submit.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
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
    private static final int GZIP_HEADER_SIZE = 10;
    private static final int GZIP_TRAILER_SIZE = 8;
    private static final int MIN_GZIP_MEMBER_LENGTH = GZIP_HEADER_SIZE + GZIP_TRAILER_SIZE;
    private static final int GZIP_ID_1 = 0x1f;
    private static final int GZIP_ID_2 = 0x8b;
    private static final int GZIP_DEFLATE_METHOD = 8;
    private static final int GZIP_RESERVED_FLAGS = 0xe0;
    private static final int GZIP_HEADER_CRC_FLAG = 0x02;
    private static final int GZIP_EXTRA_FIELD_FLAG = 0x04;
    private static final int GZIP_FILE_NAME_FLAG = 0x08;
    private static final int GZIP_COMMENT_FLAG = 0x10;
    private static final int BYTE_MASK = 0xff;
    private static final int SHORT_MASK = 0xffff;
    private static final int BYTE_SHIFT_1 = 8;
    private static final int BYTE_SHIFT_2 = 16;
    private static final int BYTE_SHIFT_3 = 24;
    private static final String MANIFEST = "datapackage.json";
    private static final String INDEX = "indexes/index.cdxj";
    private static final String WACZ_VERSION = "1.1.1";
    private static final Pattern ARCHIVE_PATH = Pattern.compile("archive/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.warc\\.gz");
    private static final Pattern CDXJ_TIMESTAMP = Pattern.compile("[0-9]{14}");
    private final ObjectMapper objectMapper;

    public WaczReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Snapshot read(byte[] content) {
        Map<String, byte[]> entries = readEntries(content);
        String archivePath = validateManifest(entries.get(MANIFEST), entries);
        Map<String, IndexEntry> index = validateIndex(entries.get(INDEX), archivePath,
                entries.get(archivePath).length);
        List<Resource> resources = readWarc(entries.get(archivePath));
        validateResources(resources);
        validateIndexedResources(index, resources, entries.get(archivePath));
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
                    if (entries.putIfAbsent(entry.getName(), bytes) != null) {
                        throw invalid();
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw invalid();
        }
        return entries;
    }

    private String validateManifest(byte[] bytes, Map<String, byte[]> entries) {
        if (bytes == null || bytes.length == 0) {
            throw invalid();
        }
        try {
            return manifestArchivePath(objectMapper.readTree(bytes), entries);
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private String manifestArchivePath(JsonNode manifest, Map<String, byte[]> entries) {
        if (!hasSupportedManifestHeader(manifest)) {
            throw invalid();
        }
        JsonNode resources = manifest.path("resources");
        if (!isExpectedResourceArray(resources)) {
            throw invalid();
        }
        return declaredArchivePath(manifestResources(resources), entries);
    }

    private Map<String, String> manifestResources(JsonNode resources) {
        Map<String, String> paths = new HashMap<>();
        for (JsonNode resource : resources) {
            addManifestResource(paths, resource);
        }
        return paths;
    }

    private String declaredArchivePath(Map<String, String> paths, Map<String, byte[]> entries) {
        String archivePath = paths.get("archive");
        if (archivePath == null || !paths.containsKey("index")) {
            throw invalid();
        }
        if (!entries.containsKey(archivePath) || !entries.containsKey(INDEX)) {
            throw invalid();
        }
        return archivePath;
    }

    private boolean hasSupportedManifestHeader(JsonNode manifest) {
        return manifest.isObject() && "data-package".equals(manifest.path("profile").asText())
                && WACZ_VERSION.equals(manifest.path("wacz_version").asText());
    }

    private boolean isExpectedResourceArray(JsonNode resources) {
        return resources.isArray() && resources.size() == 2;
    }

    private void addManifestResource(Map<String, String> paths, JsonNode resource) {
        if (!resource.isObject()) {
            throw invalid();
        }
        String name = textField(resource, "name");
        String path = textField(resource, "path");
        String mediaType = textField(resource, "mediatype");
        if (!hasRequiredResourceFields(name, path, mediaType)) {
            throw invalid();
        }
        if (!isValidArchiveResource(name, path, mediaType) && !isValidIndexResource(name, path, mediaType)) {
            throw invalid();
        }
        if (paths.putIfAbsent(name, path) != null) {
            throw invalid();
        }
    }

    private boolean hasRequiredResourceFields(String name, String path, String mediaType) {
        return name != null && path != null && mediaType != null;
    }

    private boolean isValidArchiveResource(String name, String path, String mediaType) {
        return "archive".equals(name) && ARCHIVE_PATH.matcher(path).matches()
                && "application/gzip".equals(mediaType);
    }

    private boolean isValidIndexResource(String name, String path, String mediaType) {
        return "index".equals(name) && INDEX.equals(path) && "application/x-cdxj".equals(mediaType);
    }

    private String textField(JsonNode object, String field) {
        JsonNode value = object.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private Map<String, IndexEntry> validateIndex(byte[] index, String archivePath, long archiveLength) {
        if (index == null || index.length == 0) {
            throw invalid();
        }
        Map<String, IndexEntry> indexedResources = new LinkedHashMap<>();
        String text = new String(index, StandardCharsets.UTF_8);
        addIndexLines(text.split("\\R", -1), archivePath, archiveLength, indexedResources);
        if (indexedResources.isEmpty()) {
            throw invalid();
        }
        return indexedResources;
    }

    private void addIndexLines(String[] lines, String archivePath, long archiveLength,
            Map<String, IndexEntry> indexedResources) {
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            if (lines[lineNumber].isEmpty() && lineNumber == lines.length - 1) {
                continue;
            }
            if (lines[lineNumber].isEmpty()) {
                throw invalid();
            }
            addIndexEntry(lines[lineNumber], archivePath, archiveLength, indexedResources);
        }
    }

    private void addIndexEntry(String line, String archivePath, long archiveLength,
            Map<String, IndexEntry> indexedResources) {
        IndexEntry parsed = parseIndexEntry(line, archivePath, archiveLength);
        if (indexedResources.putIfAbsent(parsed.url(), parsed) != null) {
            throw invalid();
        }
    }

    private IndexEntry parseIndexEntry(String line, String archivePath, long archiveLength) {
        int secondSpace = indexJsonStart(line);
        int firstSpace = line.indexOf(' ');
        String url = line.substring(0, firstSpace);
        try {
            JsonNode entry = objectMapper.readTree(line.substring(secondSpace));
            String mimeType = textField(entry, "mime");
            if (!isValidIndexEntry(entry, url, archivePath, archiveLength, mimeType)) {
                throw invalid();
            }
            return new IndexEntry(url, mimeType, entry.path("offset").asLong(), entry.path("length").asLong());
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private int indexJsonStart(String line) {
        int firstSpace = line.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 : line.indexOf(' ', firstSpace + 1);
        if (firstSpace <= 0 || secondSpace <= firstSpace + 1) {
            throw invalid();
        }
        String timestamp = line.substring(firstSpace + 1, secondSpace);
        if (!CDXJ_TIMESTAMP.matcher(timestamp).matches()) {
            throw invalid();
        }
        return secondSpace + 1;
    }

    private boolean isValidIndexEntry(JsonNode entry, String url, String archivePath, long archiveLength,
            String mimeType) {
        if (!entry.isObject()) {
            return false;
        }
        if (!hasIndexIdentity(entry, url, archivePath)) {
            return false;
        }
        if (!entry.path("status").isTextual()) {
            return false;
        }
        return hasValidIndexMetadata(entry, mimeType, archiveLength);
    }

    private boolean hasIndexIdentity(JsonNode entry, String url, String archivePath) {
        return url.equals(textField(entry, "url")) && archivePath.equals(textField(entry, "filename"));
    }

    private boolean hasValidIndexMetadata(JsonNode entry, String mimeType, long archiveLength) {
        return mimeType != null && !mimeType.isBlank() && hasValidIndexRange(entry, archiveLength)
                && "200".equals(entry.path("status").asText());
    }

    private boolean hasValidIndexRange(JsonNode entry, long archiveLength) {
        JsonNode offset = entry.path("offset");
        JsonNode length = entry.path("length");
        if (!hasIntegralIndexBounds(offset, length)) {
            return false;
        }
        long start = offset.asLong();
        long size = length.asLong();
        return isIndexRangeWithinArchive(start, size, archiveLength);
    }

    private boolean hasIntegralIndexBounds(JsonNode offset, JsonNode length) {
        return offset.isIntegralNumber() && length.isIntegralNumber()
                && offset.canConvertToLong() && length.canConvertToLong();
    }

    private boolean isIndexRangeWithinArchive(long start, long size, long archiveLength) {
        return start >= 0 && size > 0 && start <= archiveLength && size <= archiveLength - start;
    }

    private void validateIndexedResources(Map<String, IndexEntry> index, List<Resource> resources,
            byte[] compressedArchive) {
        Map<String, String> actualResources = new LinkedHashMap<>();
        for (Resource resource : resources) {
            if (actualResources.putIfAbsent(resource.url(), resource.mimeType()) != null) {
                throw invalid();
            }
        }
        validateIndexRanges(index, compressedArchive);
        Map<String, String> indexedResources = new LinkedHashMap<>();
        index.values().forEach(entry -> indexedResources.put(entry.url(), entry.mimeType()));
        if (!indexedResources.equals(actualResources)) {
            throw invalid();
        }
    }

    private void validateIndexRanges(Map<String, IndexEntry> index, byte[] compressedArchive) {
        List<IndexEntry> entries = new ArrayList<>(index.values());
        entries.sort(java.util.Comparator.comparingLong(IndexEntry::offset));
        long expectedOffset = 0;
        for (IndexEntry entry : entries) {
            if (entry.offset() != expectedOffset) {
                throw invalid();
            }
            int start = Math.toIntExact(entry.offset());
            int end = Math.toIntExact(entry.offset() + entry.length());
            byte[] compressedRecord = java.util.Arrays.copyOfRange(compressedArchive, start, end);
            Resource resource = readSingleWarcResource(decompressGzipMember(compressedRecord));
            if (!entry.url().equals(resource.url()) || !entry.mimeType().equals(resource.mimeType())) {
                throw invalid();
            }
            expectedOffset += entry.length();
        }
        if (expectedOffset != compressedArchive.length) {
            throw invalid();
        }
    }

    private byte[] decompressGzipMember(byte[] compressed) {
        int dataOffset = gzipDataOffset(compressed);
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(compressed, dataOffset, compressed.length - dataOffset);
            byte[] uncompressed = inflateLimited(inflater);
            validateGzipTrailer(compressed, dataOffset, inflater.getBytesRead(), uncompressed);
            return uncompressed;
        } catch (DataFormatException exception) {
            throw invalid();
        } finally {
            inflater.end();
        }
    }

    private byte[] inflateLimited(Inflater inflater) throws DataFormatException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            if (count == 0 && !inflater.finished()) {
                throw invalid();
            }
            if (output.size() + count > MAX_UNZIPPED_BYTES) {
                throw invalid();
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void validateGzipTrailer(byte[] compressed, int dataOffset, long deflatedLength,
            byte[] uncompressed) {
        int trailerOffset = dataOffset + Math.toIntExact(deflatedLength);
        if (trailerOffset + GZIP_TRAILER_SIZE != compressed.length) {
            throw invalid();
        }
        CRC32 checksum = new CRC32();
        checksum.update(uncompressed);
        if (checksum.getValue() != readLittleEndianUnsignedInt(compressed, trailerOffset)) {
            throw invalid();
        }
        if (Integer.toUnsignedLong(uncompressed.length)
                != readLittleEndianUnsignedInt(compressed, trailerOffset + Integer.BYTES)) {
            throw invalid();
        }
    }

    private Resource readSingleWarcResource(byte[] warc) {
        WarcRecord record = recordAt(warc, 0);
        if (record == null || !isResponse(record.header()) || nextRecordOffset(warc, record) != warc.length) {
            throw invalid();
        }
        Resource resource = response(headerValue(record.header(), "WARC-Target-URI"), record.content());
        if (resource == null) {
            throw invalid();
        }
        return resource;
    }

    private int gzipDataOffset(byte[] compressed) {
        if (compressed.length < MIN_GZIP_MEMBER_LENGTH) {
            throw invalid();
        }
        if ((compressed[0] & BYTE_MASK) != GZIP_ID_1 || (compressed[1] & BYTE_MASK) != GZIP_ID_2) {
            throw invalid();
        }
        if ((compressed[2] & BYTE_MASK) != GZIP_DEFLATE_METHOD) {
            throw invalid();
        }
        int flags = compressed[3] & BYTE_MASK;
        if ((flags & GZIP_RESERVED_FLAGS) != 0) {
            throw invalid();
        }
        int offset = GZIP_HEADER_SIZE;
        offset = skipGzipExtraField(compressed, offset, flags);
        offset = skipGzipTextField(compressed, offset, flags, GZIP_FILE_NAME_FLAG);
        offset = skipGzipTextField(compressed, offset, flags, GZIP_COMMENT_FLAG);
        offset = skipGzipHeaderChecksum(compressed, offset, flags);
        if (offset > compressed.length - GZIP_TRAILER_SIZE) {
            throw invalid();
        }
        return offset;
    }

    private int skipGzipExtraField(byte[] compressed, int offset, int flags) {
        if ((flags & GZIP_EXTRA_FIELD_FLAG) == 0) {
            return offset;
        }
        int extraLength = readLittleEndianUnsignedShort(compressed, offset);
        int dataOffset = offset + Short.BYTES;
        ensureAvailable(compressed, dataOffset, extraLength);
        return dataOffset + extraLength;
    }

    private int skipGzipTextField(byte[] compressed, int offset, int flags, int flag) {
        return (flags & flag) == 0 ? offset : skipZeroTerminated(compressed, offset);
    }

    private int skipGzipHeaderChecksum(byte[] compressed, int offset, int flags) {
        if ((flags & GZIP_HEADER_CRC_FLAG) == 0) {
            return offset;
        }
        ensureAvailable(compressed, offset, Short.BYTES);
        CRC32 checksum = new CRC32();
        checksum.update(compressed, 0, offset);
        if (((int) checksum.getValue() & SHORT_MASK) != readLittleEndianUnsignedShort(compressed, offset)) {
            throw invalid();
        }
        return offset + Short.BYTES;
    }

    private int skipZeroTerminated(byte[] bytes, int offset) {
        while (offset < bytes.length && bytes[offset] != 0) {
            offset++;
        }
        if (offset >= bytes.length) {
            throw invalid();
        }
        return offset + 1;
    }

    private void ensureAvailable(byte[] bytes, int offset, int count) {
        if (offset < 0 || count < 0 || offset > bytes.length - count) {
            throw invalid();
        }
    }

    private int readLittleEndianUnsignedShort(byte[] bytes, int offset) {
        ensureAvailable(bytes, offset, Short.BYTES);
        return (bytes[offset] & BYTE_MASK) | (bytes[offset + 1] & BYTE_MASK) << BYTE_SHIFT_1;
    }

    private long readLittleEndianUnsignedInt(byte[] bytes, int offset) {
        ensureAvailable(bytes, offset, Integer.BYTES);
        long first = bytes[offset] & BYTE_MASK;
        long second = bytes[offset + 1] & BYTE_MASK;
        long third = bytes[offset + 2] & BYTE_MASK;
        long fourth = bytes[offset + 3] & BYTE_MASK;
        return first | second << BYTE_SHIFT_1 | third << BYTE_SHIFT_2 | fourth << BYTE_SHIFT_3;
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
                throw invalid();
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
        if (!header.startsWith("WARC/1.0") && !header.startsWith("WARC/1.1")) {
            throw invalid();
        }
        int recordLength = contentLength(header);
        int recordStart = headerEnd + HEADER_DELIMITER_LENGTH;
        if (recordLength < 0 || recordLength > warc.length - recordStart) {
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

    private record IndexEntry(String url, String mimeType, long offset, long length) {
    }
}
