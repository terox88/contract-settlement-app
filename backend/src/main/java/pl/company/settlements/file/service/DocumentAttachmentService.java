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
            String description
    ) {

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
                        description
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
            String description
    ) {

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
                        description
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
            String description
    ) {

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
                        description
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
            String description
    ) {

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
                        description
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
            String description
    ) {

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
                        description
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
            String description
    ) {

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
                        description
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
                storedFile.getOriginalFilename(),
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

    private DocumentAttachment createAttachment(
            MultipartFile file,
            AttachmentType type,
            String description
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

        StoredFile storedFile;

        try (InputStream inputStream =
                     file.getInputStream()) {

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

        DocumentAttachment savedAttachment;

        try {

            savedAttachment =
                    repository.saveAndFlush(
                            attachment
                    );

        } catch (RuntimeException exception) {

            fileStorageService.delete(
                    attachment
                            .getStoredFile()
                            .getStorageKey()
            );

            throw exception;
        }

        return toResponse(
                savedAttachment
        );
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

    private AttachmentResponse toResponse(
            DocumentAttachment attachment
    ) {

        StoredFile storedFile =
                attachment.getStoredFile();

        return AttachmentResponse.builder()
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