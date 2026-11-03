package at.ac.tgm.submit.archive;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** In-memory upload adapter for the opt-in testing-only artifact fixture. */
final class TestingPreviewFile implements MultipartFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    TestingPreviewFile(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content.clone();
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.write(destination.toPath(), content);
    }
}
