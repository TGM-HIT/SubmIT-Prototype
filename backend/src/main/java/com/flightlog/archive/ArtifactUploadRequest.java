package com.flightlog.archive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Multipart upload form; the artefact is stored privately and never published by upload.")
public class ArtifactUploadRequest {
    @NotNull
    private ArtifactType type;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    @Schema(description = "Optional description.")
    private String description;

    @NotNull
    @Schema(type = "string", format = "binary", description = "Artefact file, at most 10 MiB.")
    private MultipartFile file;

    @Size(max = 4000)
    @Schema(description = "Optional notes for the later publication review.")
    private String publicationSubmissionMetadata;

    public ArtifactType getType() {
        return type;
    }

    public void setType(ArtifactType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getPublicationSubmissionMetadata() {
        return publicationSubmissionMetadata;
    }

    public void setPublicationSubmissionMetadata(String publicationSubmissionMetadata) {
        this.publicationSubmissionMetadata = publicationSubmissionMetadata;
    }
}
