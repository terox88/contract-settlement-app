package pl.company.settlements.file.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "document_attachment")
public class DocumentAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentType type;

    private String description;
}
