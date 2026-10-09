package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

        return toResponse(savedStatement);
    }

    @Transactional
    public SubcontractorStatementResponse update(
            Long id,
            SubcontractorStatementUpdateRequest request
    ) {
        SubcontractorStatement statement =
                findEntityById(id);

        validateStatementDate(
                statement.getSubcontractorInvoice(),
                request.getStatementDate()
        );

        applyData(
                statement,
                request.getStatementDate(),
                request.getRemarks()
        );

        return toResponse(statement);
    }

    @Transactional
    public void delete(Long id) {
        SubcontractorStatement statement =
                findEntityById(id);

        SubcontractorInvoice invoice =
                statement.getSubcontractorInvoice();

        invoice.setStatement(null);

        repository.delete(statement);
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