package pl.company.settlements.file.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.payment.domain.Payment;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "document_attachment")
public class DocumentAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stored_file_id", nullable = false)
    private StoredFile file;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false)
    private AttachmentType type;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_contractor_invoice_id")
    private MainContractorInvoice mainContractorInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcontractor_invoice_id")
    private SubcontractorInvoice subcontractorInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_amendment_id")
    private ContractAmendment contractAmendment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;
}
