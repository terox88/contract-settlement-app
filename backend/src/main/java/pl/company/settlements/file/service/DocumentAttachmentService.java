package pl.company.settlements.file.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.company.settlements.contract.domain.Contract;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.repository.ContractAmendmentRepository;
import pl.company.settlements.contract.repository.ContractRepository;
import pl.company.settlements.file.domain.AttachmentType;
import pl.company.settlements.file.domain.AttachmentOwner;
import pl.company.settlements.file.domain.DocumentAttachment;
import pl.company.settlements.file.domain.StoredFile;
import pl.company.settlements.file.model.AttachmentDownload;
import pl.company.settlements.file.model.AttachmentResponse;
import pl.company.settlements.file.repository.DocumentAttachmentRepository;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorStatementRepository;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.repository.PaymentRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentAttachmentService {

    private final DocumentAttachmentRepository repository;

    private final FileStorageService fileStorageService;

    private final ContractRepository contractRepository;

    private final ContractAmendmentRepository
            contractAmendmentRepository;

    private final MainContractorInvoiceRepository
            mainContractorInvoiceRepository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    private final PaymentRepository paymentRepository;

    private final SubcontractorStatementRepository
            subcontractorStatementRepository;
    private final PdfPageSelectionService pdfPageSelectionService;

    private final PdfSplitService pdfSplitService;

    public AttachmentResponse getById(
            Long id
    ) {
        return toResponse(
                findEntityById(id)
        );
    }

    public List<AttachmentResponse> findAllByContractId(
            Long contractId,
            AttachmentType type
    ) {

        ensureContractExists(
                contractId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllByContract_IdOrderByIdAsc(
                                contractId
                        )
                        : repository
                        .findAllByContract_IdAndTypeOrderByIdAsc(
                                contractId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    public List<AttachmentResponse>
    findAllByContractAmendmentId(
            Long amendmentId,
            AttachmentType type
    ) {

        ensureContractAmendmentExists(
                amendmentId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllByContractAmendment_IdOrderByIdAsc(
                                amendmentId
                        )
                        : repository
                        .findAllByContractAmendment_IdAndTypeOrderByIdAsc(
                                amendmentId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    public List<AttachmentResponse>
    findAllByMainContractorInvoiceId(
            Long invoiceId,
            AttachmentType type
    ) {

        ensureMainContractorInvoiceExists(
                invoiceId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllByMainContractorInvoice_IdOrderByIdAsc(
                                invoiceId
                        )
                        : repository
                        .findAllByMainContractorInvoice_IdAndTypeOrderByIdAsc(
                                invoiceId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    public List<AttachmentResponse>
    findAllBySubcontractorInvoiceId(
            Long invoiceId,
            AttachmentType type
    ) {

        ensureSubcontractorInvoiceExists(
                invoiceId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllBySubcontractorInvoice_IdOrderByIdAsc(
                                invoiceId
                        )
                        : repository
                        .findAllBySubcontractorInvoice_IdAndTypeOrderByIdAsc(
                                invoiceId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    public List<AttachmentResponse> findAllByPaymentId(
            Long paymentId,
            AttachmentType type
    ) {

        ensurePaymentExists(
                paymentId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllByPayment_IdOrderByIdAsc(
                                paymentId
                        )
                        : repository
                        .findAllByPayment_IdAndTypeOrderByIdAsc(
                                paymentId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    public List<AttachmentResponse>
    findAllBySubcontractorStatementId(
            Long statementId,
            AttachmentType type
    ) {

        ensureSubcontractorStatementExists(
                statementId
        );

        List<DocumentAttachment> attachments =
                type == null
                        ? repository
                        .findAllBySubcontractorStatement_IdOrderByIdAsc(
                                statementId
                        )
                        : repository
                        .findAllBySubcontractorStatement_IdAndTypeOrderByIdAsc(
                                statementId,
                                type
                        );

        return toResponses(
                attachments
        );
    }

    @Transactional
    public AttachmentResponse uploadToContract(
            Long contractId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.CONTRACT.validate(type);

        Contract contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Contract with id "
                                                + contractId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setContract(
                contract
        );

        return saveAttachment(
                attachment
        );
    }

    @Transactional
    public AttachmentResponse uploadToContractAmendment(
            Long amendmentId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.AMENDMENT.validate(type);

        ContractAmendment amendment =
                contractAmendmentRepository
                        .findById(amendmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Contract amendment with id "
                                                + amendmentId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setContractAmendment(
                amendment
        );

        return saveAttachment(
                attachment
        );
    }

    @Transactional
    public AttachmentResponse uploadToMainContractorInvoice(
            Long invoiceId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.MAIN_INVOICE.validate(type);

        MainContractorInvoice invoice =
                mainContractorInvoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Main contractor invoice with id "
                                                + invoiceId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setMainContractorInvoice(
                invoice
        );

        return saveAttachment(
                attachment
        );
    }

    @Transactional
    public AttachmentResponse uploadToSubcontractorInvoice(
            Long invoiceId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.SUB_INVOICE.validate(type);

        SubcontractorInvoice invoice =
                subcontractorInvoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor invoice with id "
                                                + invoiceId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setSubcontractorInvoice(
                invoice
        );

        return saveAttachment(
                attachment
        );
    }

    @Transactional
    public AttachmentResponse uploadToPayment(
            Long paymentId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.PAYMENT.validate(type);

        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment with id "
                                                + paymentId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setPayment(
                payment
        );

        return saveAttachment(
                attachment
        );
    }

    @Transactional
    public AttachmentResponse uploadToSubcontractorStatement(
            Long statementId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {
        AttachmentOwner.STATEMENT.validate(type);

        SubcontractorStatement statement =
                subcontractorStatementRepository
                        .findById(statementId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor statement with id "
                                                + statementId
                                                + " does not exist"
                                )
                        );

        DocumentAttachment attachment =
                createAttachment(
                        file,
                        type,
                        description,
                        pages
                );

        attachment.setSubcontractorStatement(
                statement
        );

        return saveAttachment(
                attachment
        );
    }

    public AttachmentDownload download(
            Long attachmentId
    ) {

        DocumentAttachment attachment =
                findEntityById(
                        attachmentId
                );

        StoredFile storedFile =
                attachment.getStoredFile();

        return new AttachmentDownload(
                fileStorageService.load(
                        storedFile.getStorageKey()
                ),
                AttachmentNames.downloadName(attachment.getDisplayName(), storedFile.getOriginalFilename()),
                storedFile.getContentType(),
                storedFile.getSize()
        );
    }

    @Transactional
    public void delete(
            Long attachmentId
    ) {

        DocumentAttachment attachment =
                findEntityById(
                        attachmentId
                );

        String storageKey =
                attachment
                        .getStoredFile()
                        .getStorageKey();

        repository.delete(
                attachment
        );

        repository.flush();

        fileStorageService.delete(
                storageKey
        );
    }

    @Transactional
    public List<AttachmentResponse> splitToContract(
            Long contractId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.CONTRACT.validate(type);

        Contract contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Contract with id "
                                                + contractId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setContract(
                                contract
                        )
        );
    }

    @Transactional
    public List<AttachmentResponse>
    splitToContractAmendment(
            Long amendmentId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.AMENDMENT.validate(type);

        ContractAmendment amendment =
                contractAmendmentRepository
                        .findById(amendmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Contract amendment with id "
                                                + amendmentId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setContractAmendment(
                                amendment
                        )
        );
    }

    @Transactional
    public List<AttachmentResponse>
    splitToMainContractorInvoice(
            Long invoiceId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.MAIN_INVOICE.validate(type);

        MainContractorInvoice invoice =
                mainContractorInvoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Main contractor invoice with id "
                                                + invoiceId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setMainContractorInvoice(
                                invoice
                        )
        );
    }

    @Transactional
    public List<AttachmentResponse>
    splitToSubcontractorInvoice(
            Long invoiceId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.SUB_INVOICE.validate(type);

        SubcontractorInvoice invoice =
                subcontractorInvoiceRepository
                        .findById(invoiceId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor invoice with id "
                                                + invoiceId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setSubcontractorInvoice(
                                invoice
                        )
        );
    }

    @Transactional
    public List<AttachmentResponse> splitToPayment(
            Long paymentId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.PAYMENT.validate(type);

        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment with id "
                                                + paymentId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setPayment(
                                payment
                        )
        );
    }

    @Transactional
    public List<AttachmentResponse>
    splitToSubcontractorStatement(
            Long statementId,
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts
    ) {
        AttachmentOwner.STATEMENT.validate(type);

        SubcontractorStatement statement =
                subcontractorStatementRepository
                        .findById(statementId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor statement with id "
                                                + statementId
                                                + " does not exist"
                                )
                        );

        return createSplitAttachments(
                file,
                type,
                description,
                parts,
                attachment ->
                        attachment.setSubcontractorStatement(
                                statement
                        )
        );
    }


    private List<AttachmentResponse> createSplitAttachments(
            MultipartFile file,
            AttachmentType type,
            String description,
            String parts,
            Consumer<DocumentAttachment> ownerSetter
    ) {

        validateFile(
                file
        );

        if (type == null) {
            throw new IllegalArgumentException(
                    "Attachment type cannot be null"
            );
        }

        String normalizedDescription =
                trimToNull(
                        description
                );

        if (normalizedDescription != null
                && normalizedDescription.length() > 1000) {

            throw new IllegalArgumentException(
                    "Attachment description cannot exceed 1000 characters"
            );
        }

        List<PdfSplitService.SplitPdfPart>
                splitParts =
                pdfSplitService.split(
                        file,
                        parts
                );

        List<DocumentAttachment> attachments =
                new ArrayList<>();

        for (PdfSplitService.SplitPdfPart part
                : splitParts) {

            StoredFile storedFile =
                    fileStorageService.save(
                            new ByteArrayInputStream(
                                    part.content()
                            ),
                            part.filename(),
                            "application/pdf"
                    );

            DocumentAttachment attachment =
                    new DocumentAttachment(
                            storedFile,
                            type
                    );

            attachment.setDescription(
                    normalizedDescription
            );

            ownerSetter.accept(
                    attachment
            );

            attachments.add(
                    attachment
            );
        }

        return repository
                .saveAllAndFlush(
                        attachments
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DocumentAttachment createAttachment(
            MultipartFile file,
            AttachmentType type,
            String description,
            String pages
    ) {

        validateFile(file);

        if (type == null) {
            throw new IllegalArgumentException(
                    "Attachment type cannot be null"
            );
        }

        String normalizedDescription =
                trimToNull(
                        description
                );

        if (normalizedDescription != null
                && normalizedDescription.length() > 1000) {

            throw new IllegalArgumentException(
                    "Attachment description cannot exceed 1000 characters"
            );
        }

        StoredFile storedFile;

        if (pages != null
                && !pages.isBlank()) {

            byte[] selectedPdf =
                    pdfPageSelectionService.selectPages(
                            file,
                            pages
                    );

            try (
                    InputStream inputStream =
                            new java.io.ByteArrayInputStream(
                                    selectedPdf
                            )
            ) {

                storedFile =
                        fileStorageService.save(
                                inputStream,
                                file.getOriginalFilename(),
                                "application/pdf"
                        );

            } catch (IOException exception) {

                throw new IllegalStateException(
                        "Could not store processed PDF file",
                        exception
                );
            }

        } else {

            try (
                    InputStream inputStream =
                            file.getInputStream()
            ) {

                storedFile =
                        fileStorageService.save(
                                inputStream,
                                file.getOriginalFilename(),
                                file.getContentType()
                        );

            } catch (IOException exception) {

                throw new IllegalStateException(
                        "Could not read uploaded file",
                        exception
                );
            }
        }

        DocumentAttachment attachment =
                new DocumentAttachment(
                        storedFile,
                        type
                );

        attachment.setDescription(
                normalizedDescription
        );

        return attachment;
    }

    private AttachmentResponse saveAttachment(
            DocumentAttachment attachment
    ) {

        // Plik jest usuwany przez synchronizację transakcji, jeśli zapis metadanych zostanie wycofany.
        return toResponse(repository.saveAndFlush(attachment));
    }

    private DocumentAttachment findEntityById(
            Long id
    ) {

        return repository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Document attachment with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void validateFile(
            MultipartFile file
    ) {

        if (file == null
                || file.isEmpty()) {

            throw new IllegalArgumentException(
                    "File cannot be empty"
            );
        }

        if (file.getOriginalFilename() == null
                || file.getOriginalFilename()
                .isBlank()) {

            throw new IllegalArgumentException(
                    "Original filename cannot be empty"
            );
        }
    }

    private List<AttachmentResponse> toResponses(
            List<DocumentAttachment> attachments
    ) {

        return attachments.stream()
                .map(this::toResponse)
                .toList();
    }

    public AttachmentResponse toResponse(
            DocumentAttachment attachment
    ) {

        StoredFile storedFile =
                attachment.getStoredFile();

        return AttachmentResponse.builder()
                .displayName(attachment.getDisplayName() == null ? storedFile.getOriginalFilename() : attachment.getDisplayName())
                .metadataVersion(attachment.getMetadataVersion())
                .typeLabel(attachment.getType().getLabel())
                .id(
                        attachment.getId()
                )
                .type(
                        attachment.getType()
                )
                .description(
                        attachment.getDescription()
                )
                .storedFileId(
                        storedFile.getId()
                )
                .originalFilename(
                        storedFile.getOriginalFilename()
                )
                .contentType(
                        storedFile.getContentType()
                )
                .size(
                        storedFile.getSize()
                )
                .uploadedAt(
                        storedFile.getUploadedAt()
                )
                .build();
    }

    private void ensureContractExists(
            Long contractId
    ) {

        if (!contractRepository.existsById(
                contractId
        )) {

            throw new IllegalArgumentException(
                    "Contract with id "
                            + contractId
                            + " does not exist"
            );
        }
    }

    private void ensureContractAmendmentExists(
            Long amendmentId
    ) {

        if (!contractAmendmentRepository.existsById(
                amendmentId
        )) {

            throw new IllegalArgumentException(
                    "Contract amendment with id "
                            + amendmentId
                            + " does not exist"
            );
        }
    }

    private void ensureMainContractorInvoiceExists(
            Long invoiceId
    ) {

        if (!mainContractorInvoiceRepository
                .existsById(invoiceId)) {

            throw new IllegalArgumentException(
                    "Main contractor invoice with id "
                            + invoiceId
                            + " does not exist"
            );
        }
    }

    private void ensureSubcontractorInvoiceExists(
            Long invoiceId
    ) {

        if (!subcontractorInvoiceRepository
                .existsById(invoiceId)) {

            throw new IllegalArgumentException(
                    "Subcontractor invoice with id "
                            + invoiceId
                            + " does not exist"
            );
        }
    }

    private void ensurePaymentExists(
            Long paymentId
    ) {

        if (!paymentRepository.existsById(
                paymentId
        )) {

            throw new IllegalArgumentException(
                    "Payment with id "
                            + paymentId
                            + " does not exist"
            );
        }
    }

    private void ensureSubcontractorStatementExists(
            Long statementId
    ) {

        if (!subcontractorStatementRepository
                .existsById(statementId)) {

            throw new IllegalArgumentException(
                    "Subcontractor statement with id "
                            + statementId
                            + " does not exist"
            );
        }
    }

    private String trimToNull(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}
