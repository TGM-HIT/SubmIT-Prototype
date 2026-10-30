package at.ac.tgm.submit.archive;

import at.ac.tgm.submit.web.ApiException;
import org.springframework.http.HttpStatus;

public final class ArtifactErrors {
    private ArtifactErrors() {
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
