package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.audit.model.AuditOperation;
import pl.company.settlements.audit.model.AuditSnapshot;
import pl.company.settlements.audit.service.SettlementAuditService;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.invoice.model.SubcontractorStatementCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorStatementResponse;
import pl.company.settlements.invoice.model.SubcontractorStatementUpdateRequest;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.SubcontractorStatementRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractorStatementService {

    private final SettlementAuditService auditService;

    private final SubcontractorStatementRepository repository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    public SubcontractorStatementResponse getById(
            Long id
    ) {
        return toResponse(
                findEntityById(id)
        );
    }

    public SubcontractorStatementResponse
    getBySubcontractorInvoiceId(
            Long subcontractorInvoiceId
    ) {
        SubcontractorStatement statement =
                repository
                        .findBySubcontractorInvoice_Id(
                                subcontractorInvoiceId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Statement for subcontractor invoice "
                                                + subcontractorInvoiceId
                                                + " does not exist"
                                )
                        );

        return toResponse(statement);
    }

    @Transactional
    public SubcontractorStatementResponse create(
            SubcontractorStatementCreateRequest request
    ) {
        SubcontractorInvoice invoice =
                subcontractorInvoiceRepository
                        .findById(
                                request.getSubcontractorInvoiceId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Subcontractor invoice with id "
                                                + request
                                                .getSubcontractorInvoiceId()
                                                + " does not exist"
                                )
                        );

        if (repository
                .existsBySubcontractorInvoice_Id(
                        invoice.getId()
                )) {

            throw new IllegalArgumentException(
                    "Subcontractor invoice with id "
                            + invoice.getId()
                            + " already has a statement"
            );
        }

        validateStatementDate(
                invoice,
                request.getStatementDate()
        );

        SubcontractorStatement statement =
                new SubcontractorStatement(
                        invoice
                );

        applyData(
                statement,
                request.getStatementDate(),
                request.getRemarks()
        );

        SubcontractorStatement savedStatement =
                repository.save(statement);


        invoice.setStatement(savedStatement);
        auditService.recordStatement(AuditOperation.SUBCONTRACTOR_STATEMENT_CREATED, savedStatement, null);

        return toResponse(savedStatement);
    }

    @Transactional
    public SubcontractorStatementResponse update(
            Long id,
            SubcontractorStatementUpdateRequest request
    ) {
        SubcontractorStatement statement =
                findEntityForUpdate(id);

        validateStatementDate(
                statement.getSubcontractorInvoice(),
                request.getStatementDate()
        );

        AuditSnapshot before = auditService.statementSnapshot(statement);

        applyData(
                statement,
                request.getStatementDate(),
                request.getRemarks()
        );

        auditService.recordStatement(AuditOperation.SUBCONTRACTOR_STATEMENT_UPDATED, statement, before);
        return toResponse(statement);
    }

    @Transactional
    public void delete(Long id) {
        SubcontractorStatement statement =
                findEntityForUpdate(id);

        if (!statement.getAttachments().isEmpty()) {
            throw new IllegalStateException("Przed usunięciem oświadczenia usuń jego załączniki.");
        }

        SubcontractorInvoice invoice =
                statement.getSubcontractorInvoice();

        auditService.recordStatementDeletion(statement);
        invoice.setStatement(null);

        repository.delete(statement);
    }

    private SubcontractorStatement findEntityForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono oświadczenia podwykonawcy."));
    }

    private SubcontractorStatement findEntityById(
            Long id
    ) {
        return repository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Subcontractor statement with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void applyData(
            SubcontractorStatement statement,
            LocalDate statementDate,
            String remarks
    ) {
        statement.setStatementDate(
                statementDate
        );

        statement.setRemarks(
                trimToNull(remarks)
        );
    }

    private void validateStatementDate(
            SubcontractorInvoice invoice,
            LocalDate statementDate
    ) {
        if (statementDate.isBefore(
                invoice.getIssueDate()
        )) {
            throw new IllegalArgumentException(
                    "Statement date cannot be before "
                            + "subcontractor invoice issue date"
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

    private SubcontractorStatementResponse toResponse(
            SubcontractorStatement statement
    ) {
        SubcontractorInvoice invoice =
                statement.getSubcontractorInvoice();

        BigDecimal paidAmount =
                invoice.getPaidAmount();

        BigDecimal deductedAmount =
                invoice.getDeductedAmount();

        BigDecimal remainingAmount =
                invoice.getRemainingAmount();

        boolean invoiceSettled =
                invoice.isSettled();

        boolean settlementDataMismatch =
                !invoiceSettled;

        return SubcontractorStatementResponse.builder()
                .id(
                        statement.getId()
                )

                .subcontractorInvoiceId(
                        invoice.getId()
                )
                .subcontractorInvoiceNumber(
                        invoice.getInvoiceNumber()
                )

                .subcontractId(
                        invoice.getSubcontract()
                                .getId()
                )

                .subcontractorId(
                        invoice.getSubcontract()
                                .getSubcontractor()
                                .getId()
                )
                .subcontractorName(
                        invoice.getSubcontract()
                                .getSubcontractor()
                                .getName()
                )
                .subcontractorTaxId(
                        invoice.getSubcontract()
                                .getSubcontractor()
                                .getTaxId()
                )

                .statementDate(
                        statement.getStatementDate()
                )

                .remarks(
                        statement.getRemarks()
                )

                .invoiceGrossAmount(
                        invoice.getGrossAmount()
                )

                .paidAmount(
                        paidAmount
                )

                .deductedAmount(
                        deductedAmount
                )

                .remainingAmount(
                        remainingAmount
                )

                .invoiceSettled(
                        invoiceSettled
                )

                .settlementDataMismatch(
                        settlementDataMismatch
                )

                .build();
    }
}
