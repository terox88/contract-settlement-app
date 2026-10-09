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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentAllocationService {

    private final PaymentAllocationRepository repository;

    private final PaymentRepository paymentRepository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

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

    @Transactional
    public PaymentAllocationResponse create(
            PaymentAllocationCreateRequest request
    ) {

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

        validatePairUnique(
                payment.getId(),
                invoice.getId()
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

        PaymentAllocation allocation =
                new PaymentAllocation(
                        payment,
                        invoice
                );

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

    private void validatePairUnique(
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

        SubcontractorInvoice invoice =
                allocation.getSubcontractorInvoice();

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
}