package pl.company.settlements.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import pl.company.settlements.invoice.domain.Invoice;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAllocationService {

    private final PaymentAllocationRepository repository;

    private final PaymentRepository paymentRepository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    private final MainContractorInvoiceRepository
            mainContractorInvoiceRepository;

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
                        .findById(
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

        PaymentAllocation allocation;

        if (request.getSubcontractorInvoiceId() != null) {

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
                            .findById(
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
                        "Deducted amount must be zero "
                                + "for main contractor invoice allocation"
                );
            }

            allocation =
                    new PaymentAllocation(
                            payment,
                            invoice
                    );
        }

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
                findEntityById(id);

        if (allocation.isMainContractorInvoiceAllocation()
                && request.getDeductedAmount()
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new IllegalArgumentException(
                    "Deducted amount must be zero "
                            + "for main contractor invoice allocation"
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
                findEntityById(id);

        repository.delete(allocation);
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
                            + "must use the same currency"
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
                    "This payment is already allocated "
                            + "to this subcontractor invoice"
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
                    "This payment is already allocated "
                            + "to this main contractor invoice"
            );
        }
    }

    private void validateAmounts(
            BigDecimal allocatedAmount,
            BigDecimal deductedAmount
    ) {

        if (allocatedAmount == null
                || allocatedAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "Allocated amount must be greater than zero"
            );
        }

        if (deductedAmount == null
                || deductedAmount.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new IllegalArgumentException(
                    "Deducted amount cannot be negative"
            );
        }
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
                    "Payment allocation must reference exactly one invoice"
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
                    "Payment and invoice must use the same currency"
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

        BigDecimal newTotal =
                alreadyAllocated.add(
                        allocatedAmount
                );

        if (newTotal.compareTo(
                payment.getAmount()
        ) > 0) {

            throw new IllegalArgumentException(
                    "Total allocated amount cannot exceed "
                            + "payment amount"
            );
        }
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
                    .id(
                            allocation.getId()
                    )

                    .paymentId(
                            payment.getId()
                    )
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
                    .id(
                            allocation.getId()
                    )

                    .paymentId(
                            payment.getId()
                    )
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