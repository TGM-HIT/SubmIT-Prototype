package at.ac.tgm.submit.archive;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates the public snapshot boundary and prepares WACZ resources for the isolated viewer. */
@Service
public class PublicArchiveBrowseService {
    private final ArtifactService artifacts;
    private final WaczReader waczReader;

    public PublicArchiveBrowseService(ArtifactService artifacts, WaczReader waczReader) {
        this.artifacts = artifacts;
        this.waczReader = waczReader;
    }

    @Transactional(readOnly = true)
    public WaczReader.Snapshot browse(UUID projectId, UUID artifactId) {
        try {
            return waczReader.read(artifacts.publicSnapshotContent(projectId, artifactId));
        } catch (IllegalArgumentException exception) {
            throw ArtifactErrors.badRequest(
                    "This approved website snapshot cannot be browsed because its WACZ content is malformed or unsupported.");
        }
    }
}
