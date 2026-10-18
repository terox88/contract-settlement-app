package pl.company.settlements.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import pl.company.settlements.contract.service.ContractHierarchyValidationService;
import pl.company.settlements.contract.service.ContractSettlementValidationService;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentAllocationCreateRequest;
import pl.company.settlements.payment.model.PaymentAllocationResponse;
import pl.company.settlements.payment.model.PaymentAllocationUpdateRequest;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.TreeSet;

import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAllocationService {

    private void validateParties(Long paymentId, Invoice invoice) {
        for (PaymentAllocation existing : repository.findAllByPayment_IdOrderByIdAsc(paymentId)) {
            PaymentParties.requireSame(PaymentParties.of(existing), PaymentParties.of(invoice));
        }
    }

    private final PaymentAllocationRepository repository;

    private final PaymentRepository paymentRepository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    private final MainContractorInvoiceRepository
            mainContractorInvoiceRepository;
    private final ContractHierarchyValidationService hierarchyValidationService;
    private final ContractSettlementValidationService settlementValidationService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInvoiceContracts(Collection<Long> mainInvoiceIds, Collection<Long> subInvoiceIds) {
        var roots = new TreeSet<Long>();
        for (Long id : mainInvoiceIds) roots.add(mainContractorInvoiceRepository.findMainContractIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury GW.")));
        for (Long id : subInvoiceIds) roots.add(subcontractorInvoiceRepository.findMainContractIdById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono faktury podwykonawcy.")));
        roots.forEach(hierarchyValidationService::lockMainContract);
    }

    public PaymentAllocationResponse getById(
            Long id
    ) {

        return toResponse(
                findEntityById(id)
        );
    }

    public List<PaymentAllocationResponse>
    findAllByPaymentId(
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

        return repository
                .findAllByPayment_IdOrderByIdAsc(
                        paymentId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PaymentAllocationResponse>
    findAllBySubcontractorInvoiceId(
            Long subcontractorInvoiceId
    ) {

        if (!subcontractorInvoiceRepository
                .existsById(
                        subcontractorInvoiceId
                )) {

            throw new IllegalArgumentException(
                    "Subcontractor invoice with id "
                            + subcontractorInvoiceId
                            + " does not exist"
            );
        }

        return repository
                .findAllBySubcontractorInvoice_IdOrderByIdAsc(
                        subcontractorInvoiceId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PaymentAllocationResponse>
    findAllByMainContractorInvoiceId(
            Long mainContractorInvoiceId
    ) {

        if (!mainContractorInvoiceRepository
                .existsById(
                        mainContractorInvoiceId
                )) {

            throw new IllegalArgumentException(
                    "Main contractor invoice with id "
                            + mainContractorInvoiceId
                            + " does not exist"
            );
        }

        return repository
                .findAllByMainContractorInvoice_IdOrderByIdAsc(
                        mainContractorInvoiceId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PaymentAllocationResponse create(
            PaymentAllocationCreateRequest request
    ) {

        validateInvoiceTarget(
                request.getSubcontractorInvoiceId(),
                request.getMainContractorInvoiceId()
        );

        Payment payment =
                paymentRepository
                        .findByIdForUpdate(
                                request.getPaymentId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Payment with id "
                                                + request.getPaymentId()
                                                + " does not exist"
                                )
                        );

        validateAmounts(
                request.getAllocatedAmount(),
                request.getDeductedAmount()
        );

        validatePaymentCapacity(
                payment,
                request.getAllocatedAmount(),
                null
        );

        lockInvoiceContracts(request.getMainContractorInvoiceId() == null ? List.of() : List.of(request.getMainContractorInvoiceId()),
                request.getSubcontractorInvoiceId() == null ? List.of() : List.of(request.getSubcontractorInvoiceId()));

        PaymentAllocation allocation;

        if (request.getSubcontractorInvoiceId() != null) {

            SubcontractorInvoice invoice =
                    subcontractorInvoiceRepository
                            .findByIdForUpdate(
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

            validateParties(payment.getId(), invoice);
            validateCurrencies(
                    payment,
                    invoice
            );

            validateSubcontractorInvoicePairUnique(
                    payment.getId(),
                    invoice.getId()
            );

            allocation =
                    new PaymentAllocation(
                            payment,
                            invoice
                    );

        } else {

            MainContractorInvoice invoice =
                    mainContractorInvoiceRepository
                            .findByIdForUpdate(
                                    request.getMainContractorInvoiceId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Main contractor invoice with id "
                                                    + request
                                                    .getMainContractorInvoiceId()
                                                    + " does not exist"
                                    )
                            );

            validateParties(payment.getId(), invoice);
            validateCurrencies(
                    payment,
                    invoice
            );

            validateMainInvoicePairUnique(
                    payment.getId(),
                    invoice.getId()
            );

            if (request.getDeductedAmount()
                    .compareTo(BigDecimal.ZERO) != 0) {

                throw new IllegalArgumentException(
                        "Potrącenie musi wynosić zero "
                                + "przy rozliczeniu faktury GW."
                );
            }

            allocation =
                    new PaymentAllocation(
                            payment,
                            invoice
                    );
        }

        settlementValidationService.validateAllocation(invoiceOf(allocation), BigDecimal.ZERO, BigDecimal.ZERO,
                request.getAllocatedAmount(), request.getDeductedAmount());

        allocation.setAllocatedAmount(
                request.getAllocatedAmount()
        );

        allocation.setDeductedAmount(
                request.getDeductedAmount()
        );

        PaymentAllocation savedAllocation =
                repository.save(allocation);

        return toResponse(
                savedAllocation
        );
    }

    @Transactional
    public PaymentAllocationResponse update(
            Long id,
            PaymentAllocationUpdateRequest request
    ) {

        PaymentAllocation allocation =
                findEntityForUpdate(id);
        lockAllocationTargets(allocation);
        validateCurrencies(allocation.getPayment(), allocation.isMainContractorInvoiceAllocation()
                ? allocation.getMainContractorInvoice() : allocation.getSubcontractorInvoice());


        if (allocation.isMainContractorInvoiceAllocation()
                && request.getDeductedAmount()
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new IllegalArgumentException(
                    "Potrącenie musi wynosić zero "
                            + "przy rozliczeniu faktury GW."
            );
        }

        validateAmounts(
                request.getAllocatedAmount(),
                request.getDeductedAmount()
        );

        validatePaymentCapacity(
                allocation.getPayment(),
                request.getAllocatedAmount(),
                id
        );

        settlementValidationService.validateAllocation(invoiceOf(allocation),
                allocation.getAllocatedAmount(), allocation.getDeductedAmount(),
                request.getAllocatedAmount(), request.getDeductedAmount());

        allocation.setAllocatedAmount(
                request.getAllocatedAmount()
        );

        allocation.setDeductedAmount(
                request.getDeductedAmount()
        );

        return toResponse(
                allocation
        );
    }

    @Transactional
    public void delete(Long id) {

        PaymentAllocation allocation =
                findEntityForUpdate(id);
        lockAllocationTargets(allocation);

        repository.delete(allocation);
    }

    private PaymentAllocation findEntityForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono rozliczenia przelewu."));
    }

    private void lockAllocationTargets(PaymentAllocation allocation) {
        paymentRepository.findByIdForUpdate(allocation.getPayment().getId()).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono przelewu."));
        lockInvoiceContracts(allocation.isMainContractorInvoiceAllocation() ? List.of(allocation.getMainContractorInvoice().getId()) : List.of(),
                allocation.isSubcontractorInvoiceAllocation() ? List.of(allocation.getSubcontractorInvoice().getId()) : List.of());
        if (allocation.isMainContractorInvoiceAllocation()) {
            mainContractorInvoiceRepository.findByIdForUpdate(allocation.getMainContractorInvoice().getId()).orElseThrow();
        } else {
            subcontractorInvoiceRepository.findByIdForUpdate(allocation.getSubcontractorInvoice().getId()).orElseThrow();
        }
    }

    private Invoice invoiceOf(PaymentAllocation allocation) {
        return allocation.isMainContractorInvoiceAllocation() ? allocation.getMainContractorInvoice() : allocation.getSubcontractorInvoice();
    }

    private PaymentAllocation findEntityById(
            Long id
    ) {

        return repository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment allocation with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void validateCurrencies(
            Payment payment,
            SubcontractorInvoice invoice
    ) {

        if (!payment.getCurrency()
                .equalsIgnoreCase(
                        invoice.getCurrency()
                )) {

            throw new IllegalArgumentException(
                    "Payment and subcontractor invoice "
                            + "muszą mieć tę samą walutę."
            );
        }
    }

    private void validateSubcontractorInvoicePairUnique(
            Long paymentId,
            Long subcontractorInvoiceId
    ) {

        if (repository
                .existsByPayment_IdAndSubcontractorInvoice_Id(
                        paymentId,
                        subcontractorInvoiceId
                )) {

            throw new IllegalArgumentException(
                    "Ten przelew jest już przypisany "
                            + "do tej faktury podwykonawcy. Zmień istniejące rozliczenie."
            );
        }
    }

    private void validateMainInvoicePairUnique(
            Long paymentId,
            Long mainContractorInvoiceId
    ) {

        if (repository
                .existsByPayment_IdAndMainContractorInvoice_Id(
                        paymentId,
                        mainContractorInvoiceId
                )) {

            throw new IllegalArgumentException(
                    "Ten przelew jest już przypisany "
                            + "do tej faktury GW. Zmień istniejące rozliczenie."
            );
        }
    }

    private void validateAmounts(BigDecimal allocatedAmount, BigDecimal deductedAmount) {
        PaymentRules.money(allocatedAmount, true);
        PaymentRules.money(deductedAmount, false);
    }

    private void validateInvoiceTarget(
            Long subcontractorInvoiceId,
            Long mainContractorInvoiceId
    ) {

        boolean subcontractorSelected =
                subcontractorInvoiceId != null;

        boolean mainContractorSelected =
                mainContractorInvoiceId != null;

        if (subcontractorSelected
                == mainContractorSelected) {

            throw new IllegalArgumentException(
                    "Rozliczenie przelewu musi wskazywać dokładnie jedną fakturę."
            );
        }
    }
    private void validateCurrencies(
            Payment payment,
            Invoice invoice
    ) {

        if (!payment.getCurrency()
                .equalsIgnoreCase(
                        invoice.getCurrency()
                )) {

            throw new IllegalArgumentException(
                    "Przelew i faktura must use the same currency"
            );
        }
    }

    private void validatePaymentCapacity(
            Payment payment,
            BigDecimal allocatedAmount,
            Long excludedAllocationId
    ) {

        BigDecimal alreadyAllocated;

        if (excludedAllocationId == null) {

            alreadyAllocated =
                    repository
                            .sumAllocatedAmountByPaymentId(
                                    payment.getId()
                            );

        } else {

            alreadyAllocated =
                    repository
                            .sumAllocatedAmountByPaymentIdExcluding(
                                    payment.getId(),
                                    excludedAllocationId
                            );
        }

        PaymentRules.capacity(payment.getAmount(), alreadyAllocated, allocatedAmount);
    }

    private PaymentAllocationResponse toResponse(
            PaymentAllocation allocation
    ) {

        Payment payment =
                allocation.getPayment();

        BigDecimal totalAllocatedFromPayment =
                repository
                        .sumAllocatedAmountByPaymentId(
                                payment.getId()
                        );

        BigDecimal remainingPaymentAmount =
                payment.getAmount()
                        .subtract(
                                totalAllocatedFromPayment
                        );

        /*
         * =========================================================
         * FAKTURA GENERALNEGO WYKONAWCY
         * =========================================================
         */
        if (allocation.isMainContractorInvoiceAllocation()) {

            MainContractorInvoice invoice =
                    allocation.getMainContractorInvoice();

            BigDecimal invoicePaidAmount =
                    repository
                            .sumPaidAmountByMainContractorInvoiceId(
                                    invoice.getId()
                            );

            BigDecimal invoiceDeductedAmount =
                    BigDecimal.ZERO;

            BigDecimal invoiceRemainingAmount =
                    invoice.getGrossAmount()
                            .subtract(
                                    invoicePaidAmount
                            );

            boolean invoiceSettled =
                    invoiceRemainingAmount.compareTo(
                            BigDecimal.ZERO
                    ) <= 0;

            boolean invoiceAmountExceeded =
                    invoiceRemainingAmount.compareTo(
                            BigDecimal.ZERO
                    ) < 0;

            return PaymentAllocationResponse.builder()
                    .recipientContractorId(PaymentParties.of(invoice).recipientId())
                    .payerContractorId(PaymentParties.of(invoice).payerId())
                    .id(
                            allocation.getId()
                    )

                    .paymentId(payment.getId())
                    .paymentDate(payment.getPaymentDate())
                    .bankReference(payment.getBankReference())
                    .paymentDescription(payment.getDescription())
                    .paymentAmount(
                            payment.getAmount()
                    )
                    .paymentCurrency(
                            payment.getCurrency()
                    )

                    .allocatedAmount(
                            allocation.getAllocatedAmount()
                    )
                    .deductedAmount(
                            BigDecimal.ZERO
                    )

                    .totalAllocatedFromPayment(
                            totalAllocatedFromPayment
                    )
                    .remainingPaymentAmount(
                            remainingPaymentAmount
                    )

                    /*
                     * Faktura GW
                     */
                    .mainContractorInvoiceId(
                            invoice.getId()
                    )
                    .mainContractorInvoiceNumber(
                            invoice.getInvoiceNumber()
                    )

                    /*
                     * Umowa główna
                     */
                    .mainContractId(
                            invoice.getMainContract()
                                    .getId()
                    )
                    .mainContractNumber(
                            invoice.getMainContract()
                                    .getContractNumber()
                    )

                    /*
                     * Zadanie inwestycyjne
                     */
                    .investmentTaskId(
                            invoice.getMainContract()
                                    .getInvestmentTask()
                                    .getId()
                    )

                    /*
                     * Generalny wykonawca
                     */
                    .mainContractorId(
                            invoice.getMainContract()
                                    .getMainContractor()
                                    .getId()
                    )
                    .mainContractorName(
                            invoice.getMainContract()
                                    .getMainContractor()
                                    .getName()
                    )

                    /*
                     * Rozliczenie faktury
                     */
                    .invoiceGrossAmount(
                            invoice.getGrossAmount()
                    )
                    .invoicePaidAmount(
                            invoicePaidAmount
                    )
                    .invoiceDeductedAmount(
                            invoiceDeductedAmount
                    )
                    .invoiceRemainingAmount(
                            invoiceRemainingAmount
                    )

                    .invoiceSettled(
                            invoiceSettled
                    )
                    .invoiceAmountExceeded(
                            invoiceAmountExceeded
                    )

                    /*
                     * Oświadczenie dotyczy tylko
                     * faktury podwykonawcy.
                     */
                    .hasStatement(
                            false
                    )

                    .build();
        }

        /*
         * =========================================================
         * FAKTURA PODWYKONAWCY
         * =========================================================
         */
        if (allocation.isSubcontractorInvoiceAllocation()) {

            SubcontractorInvoice invoice =
                    allocation.getSubcontractorInvoice();

            BigDecimal invoicePaidAmount =
                    repository
                            .sumPaidAmountBySubcontractorInvoiceId(
                                    invoice.getId()
                            );

            BigDecimal invoiceDeductedAmount =
                    repository
                            .sumDeductedAmountBySubcontractorInvoiceId(
                                    invoice.getId()
                            );

            BigDecimal invoiceRemainingAmount =
                    invoice.getGrossAmount()
                            .subtract(
                                    invoicePaidAmount
                            )
                            .subtract(
                                    invoiceDeductedAmount
                            );

            boolean invoiceSettled =
                    invoiceRemainingAmount.compareTo(
                            BigDecimal.ZERO
                    ) <= 0;

            boolean invoiceAmountExceeded =
                    invoiceRemainingAmount.compareTo(
                            BigDecimal.ZERO
                    ) < 0;

            return PaymentAllocationResponse.builder()
                    .recipientContractorId(PaymentParties.of(invoice).recipientId())
                    .payerContractorId(PaymentParties.of(invoice).payerId())
                    .id(
                            allocation.getId()
                    )

                    .paymentId(payment.getId())
                    .paymentDate(payment.getPaymentDate())
                    .bankReference(payment.getBankReference())
                    .paymentDescription(payment.getDescription())
                    .paymentAmount(
                            payment.getAmount()
                    )
                    .paymentCurrency(
                            payment.getCurrency()
                    )

                    .allocatedAmount(
                            allocation.getAllocatedAmount()
                    )
                    .deductedAmount(
                            allocation.getDeductedAmount()
                    )

                    .totalAllocatedFromPayment(
                            totalAllocatedFromPayment
                    )
                    .remainingPaymentAmount(
                            remainingPaymentAmount
                    )

                    /*
                     * Faktura podwykonawcy
                     */
                    .subcontractorInvoiceId(
                            invoice.getId()
                    )
                    .subcontractorInvoiceNumber(
                            invoice.getInvoiceNumber()
                    )

                    /*
                     * Umowa podwykonawcza
                     */
                    .mainContractId(invoice.getSubcontract().getMainContract().getId())
                    .mainContractNumber(invoice.getSubcontract().getMainContract().getContractNumber())
                    .investmentTaskId(invoice.getSubcontract().getMainContract().getInvestmentTask().getId())
                    .subcontractId(
                            invoice.getSubcontract()
                                    .getId()
                    )

                    /*
                     * Podwykonawca
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

                    /*
                     * Rozliczenie faktury
                     */
                    .invoiceGrossAmount(
                            invoice.getGrossAmount()
                    )
                    .invoicePaidAmount(
                            invoicePaidAmount
                    )
                    .invoiceDeductedAmount(
                            invoiceDeductedAmount
                    )
                    .invoiceRemainingAmount(
                            invoiceRemainingAmount
                    )

                    .invoiceSettled(
                            invoiceSettled
                    )
                    .invoiceAmountExceeded(
                            invoiceAmountExceeded
                    )

                    .hasStatement(
                            invoice.hasStatement()
                    )

                    .build();
        }


        throw new IllegalStateException(
                "Payment allocation does not reference any invoice"
        );
    }
}
