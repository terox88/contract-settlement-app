package pl.company.settlements.invoice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.file.domain.DocumentAttachment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "subcontractor_statement",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_statement_subcontractor_invoice",
                        columnNames = "subcontractor_invoice_id"
                )
        }
)
public class SubcontractorStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontractor_invoice_id", nullable = false)
    private SubcontractorInvoice subcontractorInvoice;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(length = 1000)
    private String remarks;

    @OneToMany(
            mappedBy = "subcontractorStatement",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DocumentAttachment> attachments = new ArrayList<>();
}