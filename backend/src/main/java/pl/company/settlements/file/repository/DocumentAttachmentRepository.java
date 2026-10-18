package pl.company.settlements.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
        select a from DocumentAttachment a join fetch a.storedFile
        where a.contract.id = :root
           or a.contract.id in (select s.id from Subcontract s where s.mainContract.id = :root)
           or a.contractAmendment.id in (select m.id from ContractAmendment m where m.contract.id = :root
                or m.contract.id in (select s.id from Subcontract s where s.mainContract.id = :root))
           or a.mainContractorInvoice.id in (select i.id from MainContractorInvoice i where i.mainContract.id = :root)
           or a.subcontractorInvoice.id in (select i.id from SubcontractorInvoice i where i.subcontract.mainContract.id = :root)
           or a.subcontractorStatement.id in (select st.id from SubcontractorStatement st where st.subcontractorInvoice.subcontract.mainContract.id = :root)
           or a.payment.id in (select pa.payment.id from PaymentAllocation pa
                left join pa.mainContractorInvoice mi left join pa.subcontractorInvoice si
                where mi.id in (select i.id from MainContractorInvoice i where i.mainContract.id = :root)
                   or si.id in (select i.id from SubcontractorInvoice i where i.subcontract.mainContract.id = :root))
        order by a.id
        """)
    List<DocumentAttachment> findContractWorkspace(@Param("root") Long mainContractId);
}