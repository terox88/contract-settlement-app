package pl.company.settlements.invoice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.domain.SubcontractorStatement;
import pl.company.settlements.invoice.model.*;
import pl.company.settlements.invoice.repository.MainInvoiceSettlementRepository;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.invoice.repository.specification.SubcontractorInvoiceSpecification;
import pl.company.settlements.payment.model.SubcontractorInvoicePaymentAmounts;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractorInvoiceService {

    private final SubcontractorInvoiceRepository repository;
    private final ContractHierarchyValidationService hierarchyValidationService;
    private final ContractSettlementValidationService settlementValidationService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final PaymentAllocationRepository
            paymentAllocationRepository;

    private final MainInvoiceSettlementRepository
            mainInvoiceSettlementRepository;


    public SubcontractorInvoiceResponse getById(
            Long id
    ) {
        SubcontractorInvoice invoice =
                findEntityById(id);

        return toResponseWithAggregates(
                invoice
        );
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
        List<SubcontractorInvoice> invoices =
                repository.findAll(
                        SubcontractorInvoiceSpecification.fromFilter(filter),
                        Sort.by(
                                Sort.Order.desc("issueDate"),
                                Sort.Order.desc("id")
                        )
                );

        if (invoices.isEmpty()) {
            return List.of();
        }

        List<Long> invoiceIds =
                invoices.stream()
                        .map(SubcontractorInvoice::getId)
                        .toList();

        Map<Long, SubcontractorInvoicePaymentAmounts> paymentAmounts =
                paymentAllocationRepository
                        .sumAmountsBySubcontractorInvoiceIds(
                                invoiceIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SubcontractorInvoicePaymentAmounts
                                                ::getSubcontractorInvoiceId,
                                        Function.identity()
                                )
                        );

        Map<Long, BigDecimal> settlementAmounts =
                mainInvoiceSettlementRepository
                        .sumAmountsBySubcontractorInvoiceIds(
                                invoiceIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SubcontractorInvoiceSettlementAmounts
                                                ::getSubcontractorInvoiceId,
                                        SubcontractorInvoiceSettlementAmounts
                                                ::getAssignedAmount
                                )
                        );

        Map<Long, InvoiceDocumentState> documents = invoiceDocumentService.read(invoiceIds);

        return invoices.stream()
                .map(invoice -> {

                    SubcontractorInvoicePaymentAmounts payment =
                            paymentAmounts.get(
                                    invoice.getId()
                            );

                    BigDecimal paidAmount =
                            payment != null
                                    ? payment.getPaidAmount()
                                    : BigDecimal.ZERO;

                    BigDecimal deductedAmount =
                            payment != null
                                    ? payment.getDeductedAmount()
                                    : BigDecimal.ZERO;

                    BigDecimal assignedAmount =
                            settlementAmounts.getOrDefault(
                                    invoice.getId(),
                                    BigDecimal.ZERO
                            );

                    return toResponse(
                            invoice,
                            paidAmount,
                            deductedAmount,
                            assignedAmount,
                            documents.get(invoice.getId())
                    );
                })
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        SubcontractorInvoice invoice = findEntityForUpdate(id);
        if (mainInvoiceSettlementRepository.existsBySubcontractorInvoice_Id(id) || mainInvoiceSettlementRepository.existsByParentSubcontractorInvoice_Id(id)) {
            throw new IllegalStateException("Najpierw usuń przypisania tej faktury do faktur GW.");
        }
        if (!invoice.getPaymentAllocations().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete invoice that has payment allocations."
            );
        }
        if (!invoice.getAttachments().isEmpty()
                || (invoice.getStatement() != null && !invoice.getStatement().getAttachments().isEmpty())) {
            throw new IllegalStateException("Przed usunięciem faktury usuń jej załączniki i pliki oświadczenia.");
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

        Subcontract subcontract = hierarchyValidationService.lockSubcontract(request.getSubcontractId());
        settlementValidationService.validateInvoice(subcontract, BigDecimal.ZERO, request.getGrossAmount(), request.getCurrency());

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
                request.getVatRates(),
                request.getDescription()
        );

        SubcontractorInvoice savedInvoice = repository.save(invoice);

        return toResponse(
                savedInvoice,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new InvoiceDocumentState()
        );
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
        Long subcontractId = repository.findSubcontractIdById(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono faktury podwykonawcy."));
        hierarchyValidationService.lockSubcontract(subcontractId);
        SubcontractorInvoice invoice = findEntityForUpdate(id);

        if (!invoice.getPaymentAllocations().isEmpty()) {
            throw new IllegalStateException(
                    "Faktura ma przypisane płatności lub potrącenia. Nie można zmieniać jej danych."
            );
        }

        if (mainInvoiceSettlementRepository.existsByParentSubcontractorInvoice_Id(id)
                && !invoice.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new IllegalArgumentException("Nie można zmienić waluty faktury powiązanej z dalszymi podwykonawcami.");
        }
        InvoiceAssignmentRules.validateInvoiceChange(request.getGrossAmount(),
                mainInvoiceSettlementRepository.sumBySubcontractorInvoiceId(id),
                invoice.getCurrency(), request.getCurrency());

        settlementValidationService.validateInvoice(invoice.getSubcontract(), invoice.getGrossAmount(),
                request.getGrossAmount(), request.getCurrency());

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
                request.getVatRates(),
                request.getDescription()
        );

        return toResponseWithAggregates(invoice);
    }

    private SubcontractorInvoice findEntityForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono faktury podwykonawcy."));
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
            List<String> vatRates,
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
        invoice.getVatRates()
                .clear();

        invoice.getVatRates()
                .addAll(
                        normalizeVatRates(
                                vatRates
                        )
                );
        invoice.setDescription(description);
    }

    private SubcontractorInvoiceResponse toResponse(
            SubcontractorInvoice invoice,
            BigDecimal paidAmount,
            BigDecimal deductedAmount,
            BigDecimal assignedToMainInvoicesAmount,
            InvoiceDocumentState documentState
    ) {
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

        BigDecimal remainingToAssignToMainInvoices =
                invoice.getGrossAmount()
                        .subtract(
                                assignedToMainInvoicesAmount
                        );

        boolean assignedToMainInvoice =
                assignedToMainInvoicesAmount
                        .compareTo(
                                BigDecimal.ZERO
                        ) > 0;

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
                .deductionReview(DeductionReviewService.describe(invoice, paidAmount, deductedAmount, documentState != null && documentState.hasPaymentAllocations()))
                .parentSubcontractId(invoice.getSubcontract().getParentSubcontract() == null ? null : invoice.getSubcontract().getParentSubcontract().getId())
                .payerContractorId(invoice.getSubcontract().getPayerContractor() == null ? null : invoice.getSubcontract().getPayerContractor().getId())
                .documents(documentState.summary())
                .editable(!documentState.hasPaymentAllocations())
                .editBlockedReason(documentState.hasPaymentAllocations()
                        ? "Faktura ma przypisane płatności lub potrącenia. Edycja danych faktury jest zablokowana."
                        : null)
                .id(
                        invoice.getId()
                )

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
                .vatRates(
                        invoice.getVatRates()
                                .stream()
                                .toList()
                )

                .currency(
                        invoice.getCurrency()
                )
                .description(
                        invoice.getDescription()
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
                .settled(
                        settled
                )
                .amountExceeded(
                        amountExceeded
                )

                .assignedToParentInvoicesAmount(assignedToMainInvoicesAmount)
                .remainingToAssignToParentInvoices(remainingToAssignToMainInvoices)
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
                    "Kwota brutto musi być równa sumie kwot netto i VAT."
            );
        }
    }

    private SubcontractorInvoiceResponse toResponseWithAggregates(
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

        BigDecimal assignedAmount =
                mainInvoiceSettlementRepository
                        .sumBySubcontractorInvoiceId(
                                invoice.getId()
                        );

        return toResponse(
                invoice,
                paidAmount,
                deductedAmount,
                assignedAmount,
                invoiceDocumentService.read(List.of(invoice.getId())).get(invoice.getId())
        );
    }

    private List<String> normalizeVatRates(
            List<String> vatRates
    ) {

        if (vatRates == null
                || vatRates.isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one VAT rate is required"
            );
        }

        List<String> normalized =
                vatRates.stream()
                        .map(rate -> {

                            if (rate == null
                                    || rate.isBlank()) {

                                throw new IllegalArgumentException(
                                        "VAT rate cannot be blank"
                                );
                            }

                            return rate
                                    .trim()
                                    .replaceAll(
                                            "\\s+",
                                            " "
                                    )
                                    .toUpperCase(
                                            Locale.ROOT
                                    );
                        })
                        .distinct()
                        .toList();

        return normalized;
    }

}
