package pl.company.settlements.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.payment.domain.Payment;
import pl.company.settlements.payment.domain.PaymentAllocation;
import pl.company.settlements.payment.model.PaymentAllocatedAmount;
import pl.company.settlements.payment.model.PaymentCreateRequest;
import pl.company.settlements.payment.model.PaymentResponse;
import pl.company.settlements.payment.model.PaymentUpdateRequest;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.payment.repository.PaymentRepository;

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
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentAllocationRepository
            paymentAllocationRepository;

    public PaymentResponse getById(Long id) {

        Payment payment =
                findEntityById(id);

        BigDecimal allocatedAmount =
                paymentAllocationRepository
                        .sumAllocatedAmountByPaymentId(
                                payment.getId()
                        );

        return toResponse(
                payment,
                allocatedAmount
        );
    }

    public List<PaymentResponse> findAll() {

        List<Payment> payments =
                paymentRepository.findAll(
                        Sort.by(
                                Sort.Order.desc("paymentDate"),
                                Sort.Order.desc("id")
                        )
                );

        if (payments.isEmpty()) {
            return List.of();
        }

        List<Long> paymentIds =
                payments.stream()
                        .map(Payment::getId)
                        .toList();

        Map<Long, BigDecimal> allocatedAmounts =
                paymentAllocationRepository
                        .sumAllocatedAmountsByPaymentIds(
                                paymentIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PaymentAllocatedAmount::getPaymentId,
                                        PaymentAllocatedAmount::getAllocatedAmount
                                )
                        );

        return payments.stream()
                .map(payment ->
                        toResponse(
                                payment,
                                allocatedAmounts.getOrDefault(
                                        payment.getId(),
                                        BigDecimal.ZERO
                                )
                        )
                )
                .toList();
    }

    @Transactional
    public PaymentResponse create(
            PaymentCreateRequest request
    ) {

        validateAmount(
                request.getAmount()
        );

        String currency =
                normalizeCurrency(
                        request.getCurrency()
                );

        Payment payment =
                new Payment(
                        request.getPaymentDate(),
                        request.getAmount()
                );

        applyData(
                payment,
                request.getPaymentDate(),
                request.getAmount(),
                currency,
                request.getBankReference(),
                request.getDescription()
        );

        Payment savedPayment =
                paymentRepository.save(payment);

        return toResponse(
                savedPayment,
                BigDecimal.ZERO
        );
    }

    @Transactional
    public PaymentResponse update(
            Long id,
            PaymentUpdateRequest request
    ) {

        Payment payment =
                findEntityForUpdate(id);

        validateAmount(
                request.getAmount()
        );

        BigDecimal allocatedAmount =
                paymentAllocationRepository
                        .sumAllocatedAmountByPaymentId(
                                id
                        );

        if (request.getAmount()
                .compareTo(allocatedAmount) < 0) {

            throw new IllegalArgumentException(
                    "Kwota przelewu nie może być mniejsza niż "
                            + "suma kwot przypisanych do faktur."
            );
        }

        String currency =
                normalizeCurrency(
                        request.getCurrency()
                );

        validateCurrencyForExistingAllocations(
                payment,
                currency
        );

        applyData(
                payment,
                request.getPaymentDate(),
                request.getAmount(),
                currency,
                request.getBankReference(),
                request.getDescription()
        );

        return toResponse(
                payment,
                allocatedAmount
        );
    }

    @Transactional
    public void delete(Long id) {

        Payment payment =
                findEntityForUpdate(id);

        if (paymentAllocationRepository
                .existsByPayment_Id(id)) {

            throw new IllegalArgumentException(
                    "Nie można usunąć przelewu, który ma "
                            + "przypisania do faktur."
            );
        }

        paymentRepository.delete(payment);
    }

    private Payment findEntityForUpdate(Long id) {
        return paymentRepository.findByIdForUpdate(id).orElseThrow(() ->
                new IllegalArgumentException("Nie znaleziono przelewu."));
    }

    private Payment findEntityById(Long id) {

        return paymentRepository
                .findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void validateAmount(BigDecimal amount) {
        PaymentRules.money(amount, true);
    }

    private void validateCurrencyForExistingAllocations(
            Payment payment,
            String newCurrency
    ) {

        List<PaymentAllocation> allocations =
                paymentAllocationRepository
                        .findAllByPayment_IdOrderByIdAsc(
                                payment.getId()
                        );

        boolean currencyMismatch =
                allocations.stream()
                        .anyMatch(allocation -> {

                            String invoiceCurrency;

                            if (allocation
                                    .getSubcontractorInvoice()
                                    != null) {

                                invoiceCurrency =
                                        allocation
                                                .getSubcontractorInvoice()
                                                .getCurrency();

                            } else {

                                invoiceCurrency =
                                        allocation
                                                .getMainContractorInvoice()
                                                .getCurrency();
                            }

                            return !newCurrency
                                    .equalsIgnoreCase(
                                            invoiceCurrency
                                    );
                        });

        if (currencyMismatch) {

            throw new IllegalArgumentException(
                    "Nie można zmienić waluty przelewu, ponieważ "
                            + "byłaby niezgodna z walutą "
                            + "powiązanej faktury."
            );
        }
    }

    private void applyData(
            Payment payment,
            LocalDate paymentDate,
            BigDecimal amount,
            String currency,
            String bankReference,
            String description
    ) {

        payment.setPaymentDate(
                paymentDate
        );

        payment.setAmount(
                amount
        );

        payment.setCurrency(
                currency
        );

        payment.setBankReference(
                trimToNull(bankReference)
        );

        payment.setDescription(
                trimToNull(description)
        );
    }

    private String normalizeCurrency(
            String currency
    ) {

        if (currency == null
                || currency.isBlank()) {

            return "PLN";
        }

        String normalized =
                currency.trim()
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Waluta musi mieć trzyliterowy kod."
            );
        }

        return normalized;
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

    private PaymentResponse toResponse(
            Payment payment,
            BigDecimal allocatedAmount
    ) {

        BigDecimal unallocatedAmount =
                payment.getAmount()
                        .subtract(
                                allocatedAmount
                        );

        return PaymentResponse.builder()
                .id(
                        payment.getId()
                )
                .paymentDate(
                        payment.getPaymentDate()
                )
                .amount(
                        payment.getAmount()
                )
                .currency(
                        payment.getCurrency()
                )
                .bankReference(
                        payment.getBankReference()
                )
                .description(
                        payment.getDescription()
                )
                .allocatedAmount(
                        allocatedAmount
                )
                .unallocatedAmount(
                        unallocatedAmount
                )
                .fullyAllocated(
                        unallocatedAmount.compareTo(
                                BigDecimal.ZERO
                        ) == 0
                )
                .allocationCount(
                        Math.toIntExact(paymentAllocationRepository.countByPayment_Id(payment.getId()))
                )
                .build();
    }
}