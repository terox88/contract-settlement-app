package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.invoice.model.SubcontractorInvoiceCreateRequest;
import pl.company.settlements.invoice.model.SubcontractorInvoiceFilter;
import pl.company.settlements.invoice.model.SubcontractorInvoiceResponse;
import pl.company.settlements.invoice.model.SubcontractorInvoiceUpdateRequest;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.specification.SubcontractorInvoiceSpecification;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractorInvoiceService {

    private final SubcontractorInvoiceRepository repository;
    private final SubcontractRepository subcontractRepository;
    private final PaymentAllocationRepository
            paymentAllocationRepository;

    private final MainInvoiceSettlementRepository
            mainInvoiceSettlementRepository;

    public SubcontractorInvoiceResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    private SubcontractorInvoice findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subcontractor invoice with id "
                                + id
                                + " does not exist"
                ));
    }


    public List<SubcontractorInvoiceResponse> findAll(
            SubcontractorInvoiceFilter filter
    ) {
        return repository.findAll(
                        SubcontractorInvoiceSpecification.fromFilter(filter),
                        Sort.by(
                                Sort.Order.desc("issueDate"),
                                Sort.Order.desc("id")
                        )
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        SubcontractorInvoice invoice = findEntityById(id);
        if (!invoice.getPaymentAllocations().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete invoice that has payment allocations."
            );
        }
        repository.delete(invoice);
    }

    @Transactional
    public SubcontractorInvoiceResponse create(
            SubcontractorInvoiceCreateRequest request) {

        validateAmounts(
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount()
        );

        Subcontract subcontract = subcontractRepository
                .findById(request.getSubcontractId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subcontract with id "
                                + request.getSubcontractId()
                                + " does not exist"
                ));

        SubcontractorInvoice invoice =
                new SubcontractorInvoice(subcontract);


        applyInvoiceData(
                invoice,
                request.getInvoiceNumber(),
                request.getIssueDate(),
                request.getSaleDate(),
                request.getDueDate(),
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount(),
                request.getCurrency(),
                request.getVatRate(),
                request.getDescription()
        );

        SubcontractorInvoice savedInvoice = repository.save(invoice);

        return toResponse(savedInvoice);
    }

    @Transactional
    public SubcontractorInvoiceResponse update(
            Long id,
            SubcontractorInvoiceUpdateRequest request
    ) {

        validateAmounts(
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount()
        );
        SubcontractorInvoice invoice = findEntityById(id);

        if (!invoice.getPaymentAllocations().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot edit invoice that has payment allocations."
            );
        }

        applyInvoiceData(
                invoice,
                request.getInvoiceNumber(),
                request.getIssueDate(),
                request.getSaleDate(),
                request.getDueDate(),
                request.getNetAmount(),
                request.getVatAmount(),
                request.getGrossAmount(),
                request.getCurrency(),
                request.getVatRate(),
                request.getDescription()
        );

        return toResponse(invoice);
    }

    private void applyInvoiceData(
            SubcontractorInvoice invoice,
            String invoiceNumber,
            LocalDate issueDate,
            LocalDate saleDate,
            LocalDate dueDate,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount,
            String currency,
            BigDecimal vatRate,
            String description
    ) {
        invoice.setInvoiceNumber(invoiceNumber.trim());
        invoice.setIssueDate(issueDate);
        invoice.setSaleDate(saleDate);
        invoice.setDueDate(dueDate);
        invoice.setNetAmount(netAmount);
        invoice.setVatAmount(vatAmount);
        invoice.setGrossAmount(grossAmount);
        invoice.setCurrency(currency.trim().toUpperCase(Locale.ROOT));
        invoice.setVatRate(vatRate);
        invoice.setDescription(description);
    }

    private SubcontractorInvoiceResponse toResponse(
            SubcontractorInvoice invoice
    ) {
        BigDecimal paidAmount =
                paymentAllocationRepository
                        .sumPaidAmountBySubcontractorInvoiceId(
                                invoice.getId()
                        );

        BigDecimal deductedAmount =
                paymentAllocationRepository
                        .sumDeductedAmountBySubcontractorInvoiceId(
                                invoice.getId()
                        );

        BigDecimal remainingAmount =
                invoice.getGrossAmount()
                        .subtract(paidAmount)
                        .subtract(deductedAmount);

        boolean settled =
                remainingAmount.compareTo(
                        BigDecimal.ZERO
                ) <= 0;

        boolean amountExceeded =
                remainingAmount.compareTo(
                        BigDecimal.ZERO
                ) < 0;

        BigDecimal assignedToMainInvoicesAmount =
                mainInvoiceSettlementRepository
                        .sumBySubcontractorInvoiceId(
                                invoice.getId()
                        );

        BigDecimal remainingToAssignToMainInvoices =
                invoice.getGrossAmount()
                        .subtract(
                                assignedToMainInvoicesAmount
                        );

        boolean assignedToMainInvoice =
                assignedToMainInvoicesAmount
                        .compareTo(BigDecimal.ZERO) > 0;

        boolean fullyAssignedToMainInvoices =
                assignedToMainInvoicesAmount
                        .compareTo(
                                invoice.getGrossAmount()
                        ) == 0;

        SubcontractorStatement statement =
                invoice.getStatement();

        boolean hasStatement =
                statement != null;

        boolean statementSettlementMismatch =
                hasStatement && !settled;

        return SubcontractorInvoiceResponse.builder()
                .id(
                        invoice.getId()
                )

                /*
                 * Contract context
                 */
                .subcontractId(
                        invoice.getSubcontract()
                                .getId()
                )
                .subcontractNumber(
                        invoice.getSubcontract()
                                .getContractNumber()
                )

                .mainContractId(
                        invoice.getSubcontract()
                                .getMainContract()
                                .getId()
                )
                .mainContractNumber(
                        invoice.getSubcontract()
                                .getMainContract()
                                .getContractNumber()
                )

                .investmentTaskId(
                        invoice.getSubcontract()
                                .getMainContract()
                                .getInvestmentTask()
                                .getId()
                )
                .investmentTaskNumber(
                        invoice.getSubcontract()
                                .getMainContract()
                                .getInvestmentTask()
                                .getTaskNumber()
                )

                /*
                 * Subcontractor
                 */
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

                /*
                 * Invoice
                 */
                .invoiceNumber(
                        invoice.getInvoiceNumber()
                )
                .issueDate(
                        invoice.getIssueDate()
                )
                .saleDate(
                        invoice.getSaleDate()
                )
                .dueDate(
                        invoice.getDueDate()
                )

                .netAmount(
                        invoice.getNetAmount()
                )
                .vatAmount(
                        invoice.getVatAmount()
                )
                .grossAmount(
                        invoice.getGrossAmount()
                )
                .vatRate(
                        invoice.getVatRate()
                )

                .currency(
                        invoice.getCurrency()
                )

                .description(
                        invoice.getDescription()
                )

                /*
                 * Payment settlement
                 */
                .paidAmount(
                        paidAmount
                )
                .deductedAmount(
                        deductedAmount
                )
                .remainingAmount(
                        remainingAmount
                )
                .settled(
                        settled
                )
                .amountExceeded(
                        amountExceeded
                )

                /*
                 * Main invoice assignment
                 */
                .assignedToMainInvoicesAmount(
                        assignedToMainInvoicesAmount
                )
                .remainingToAssignToMainInvoices(
                        remainingToAssignToMainInvoices
                )
                .assignedToMainInvoice(
                        assignedToMainInvoice
                )
                .fullyAssignedToMainInvoices(
                        fullyAssignedToMainInvoices
                )

                /*
                 * Statement
                 */
                .hasStatement(
                        hasStatement
                )
                .statementId(
                        hasStatement
                                ? statement.getId()
                                : null
                )
                .statementDate(
                        hasStatement
                                ? statement.getStatementDate()
                                : null
                )
                .statementRemarks(
                        hasStatement
                                ? statement.getRemarks()
                                : null
                )
                .statementSettlementMismatch(
                        statementSettlementMismatch
                )

                .build();
    }
    private void validateAmounts(
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount
    ) {
        BigDecimal calculatedGross = netAmount.add(vatAmount);

        if (calculatedGross.compareTo(grossAmount) != 0) {
            throw new IllegalArgumentException(
                    "Gross amount must equal net amount plus VAT amount."
            );
        }
    }

}