package ac.at.tgm.submit.idea;

import ac.at.tgm.submit.web.ApiException;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class PdfFileValidator {
    private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    private static final int MIN_PDF_BYTES = 8;

    byte[] validate(MultipartFile file) {
        validateFileMetadata(file);
        try {
            byte[] bytes = file.getBytes();
            validateSignature(bytes);
            return bytes;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The uploaded PDF could not be read.");
        }
    }

    private void validateFileMetadata(MultipartFile file) {
        if (file == null) {
            throw requiredPdf();
        }
        String fileName = file.getOriginalFilename();
        if (file.isEmpty() || file.getSize() > MAX_PDF_BYTES || fileName == null
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw requiredPdf();
        }
    }

    private void validateSignature(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_PDF_BYTES) {
            throw invalidPdf();
        }
        if (bytes.length < MIN_PDF_BYTES) {
            throw invalidPdf();
        }
        if (!startsWithSignature(bytes)) {
            throw invalidPdf();
        }
    }

    private boolean startsWithSignature(byte[] bytes) {
        byte[] signature = {'%', 'P', 'D', 'F', '-'};
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private ApiException invalidPdf() {
        return new ApiException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid PDF.");
    }

    private ApiException requiredPdf() {
        return new ApiException(HttpStatus.BAD_REQUEST, "A non-empty PDF no larger than 10 MiB is required.");
    }
}
