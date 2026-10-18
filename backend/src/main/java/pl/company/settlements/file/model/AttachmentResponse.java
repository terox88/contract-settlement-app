package pl.company.settlements.file.model;

import lombok.Builder;
import lombok.Getter;
import pl.company.settlements.file.domain.AttachmentType;

import java.time.LocalDateTime;

@Getter
@Builder
public class AttachmentResponse {

    private Long id;

    private AttachmentType type;

    private String typeLabel;

    private String description;

    private Long storedFileId;

    private String originalFilename;

    private String displayName;

    private long metadataVersion;

    private String contentType;

    private Long size;

    private LocalDateTime uploadedAt;
}
