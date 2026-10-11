package pl.company.settlements.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.DocumentAttachment;

import java.util.List;

public interface DocumentAttachmentRepository
        extends JpaRepository<DocumentAttachment, Long> {

    List<DocumentAttachment>
    findAllByMainContractorInvoice_IdOrderByIdAsc(
            Long mainContractorInvoiceId
    );

    List<DocumentAttachment>
    findAllByMainContractorInvoice_IdAndTypeOrderByIdAsc(
            Long mainContractorInvoiceId,
            AttachmentType type
    );

    List<DocumentAttachment>
    findAllBySubcontractorInvoice_IdOrderByIdAsc(
            Long subcontractorInvoiceId
    );

    List<DocumentAttachment>
    findAllBySubcontractorInvoice_IdAndTypeOrderByIdAsc(
            Long subcontractorInvoiceId,
            AttachmentType type
    );

    List<DocumentAttachment>
    findAllByContract_IdOrderByIdAsc(
            Long contractId
    );

    List<DocumentAttachment>
    findAllByContract_IdAndTypeOrderByIdAsc(
            Long contractId,
            AttachmentType type
    );

    List<DocumentAttachment>
    findAllByContractAmendment_IdOrderByIdAsc(
            Long contractAmendmentId
    );

    List<DocumentAttachment>
    findAllByContractAmendment_IdAndTypeOrderByIdAsc(
            Long contractAmendmentId,
            AttachmentType type
    );

    List<DocumentAttachment>
    findAllByPayment_IdOrderByIdAsc(
            Long paymentId
    );

    List<DocumentAttachment>
    findAllByPayment_IdAndTypeOrderByIdAsc(
            Long paymentId,
            AttachmentType type
    );

    List<DocumentAttachment>
    findAllBySubcontractorStatement_IdOrderByIdAsc(
            Long subcontractorStatementId
    );

    List<DocumentAttachment>
    findAllBySubcontractorStatement_IdAndTypeOrderByIdAsc(
            Long subcontractorStatementId,
            AttachmentType type
    );
}