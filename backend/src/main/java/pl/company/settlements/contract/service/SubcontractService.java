package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.ContractValueBasis;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.DeductionRuleResponse;
import pl.company.settlements.contract.model.SubcontractFilter;
import pl.company.settlements.contract.model.SubcontractResponse;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contract.repository.specification.SubcontractSpecification;
import pl.company.settlements.invoice.model.SubcontractInvoicedAmount;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.model.SubcontractPaymentAmounts;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractService {

    private final SubcontractRepository repository;

    private final SubcontractorInvoiceRepository
            subcontractorInvoiceRepository;

    private final PaymentAllocationRepository
            paymentAllocationRepository;

    public SubcontractResponse getById(Long id) {
        Subcontract subcontract = findEntityById(id);

        BigDecimal invoicedAmount =
                subcontractorInvoiceRepository
                        .sumGrossAmountBySubcontractId(id);

        BigDecimal paidAmount =
                paymentAllocationRepository
                        .sumPaidAmountBySubcontractId(id);

        BigDecimal deductedAmount =
                paymentAllocationRepository
                        .sumDeductedAmountBySubcontractId(id);

        return toResponse(
                subcontract,
                invoicedAmount,
                paidAmount,
                deductedAmount
        );
    }

    public List<SubcontractResponse> findAll(
            SubcontractFilter filter
    ) {
        List<Subcontract> subcontracts = repository.findAll(
                SubcontractSpecification.fromFilter(filter),
                Sort.by(
                        Sort.Order.desc("contractDate"),
                        Sort.Order.desc("id")
                )
        );

        if (subcontracts.isEmpty()) {
            return List.of();
        }

        List<Long> subcontractIds = subcontracts.stream()
                .map(Subcontract::getId)
                .toList();

        Map<Long, BigDecimal> invoicedAmounts =
                subcontractorInvoiceRepository
                        .sumGrossAmountsBySubcontractIds(
                                subcontractIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                SubcontractInvoicedAmount::getSubcontractId,
                                SubcontractInvoicedAmount::getInvoicedAmount
                        ));

        Map<Long, SubcontractPaymentAmounts> paymentAmounts =
                paymentAllocationRepository
                        .sumAmountsBySubcontractIds(
                                subcontractIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                SubcontractPaymentAmounts::getSubcontractId,
                                amount -> amount
                        ));

        return subcontracts.stream()
                .map(subcontract -> {

                    BigDecimal invoicedAmount =
                            invoicedAmounts.getOrDefault(
                                    subcontract.getId(),
                                    BigDecimal.ZERO
                            );

                    SubcontractPaymentAmounts payments =
                            paymentAmounts.get(
                                    subcontract.getId()
                            );

                    BigDecimal paidAmount =
                            payments == null
                                    ? BigDecimal.ZERO
                                    : payments.getPaidAmount();

                    BigDecimal deductedAmount =
                            payments == null
                                    ? BigDecimal.ZERO
                                    : payments.getDeductedAmount();

                    return toResponse(
                            subcontract,
                            invoicedAmount,
                            paidAmount,
                            deductedAmount
                    );
                })
                .toList();
    }

    private Subcontract findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subcontract with id "
                                + id
                                + " does not exist"
                ));
    }

    private SubcontractResponse toResponse(
            Subcontract subcontract,
            BigDecimal invoicedAmount,
            BigDecimal paidAmount,
            BigDecimal deductedAmount
    ) {
        BigDecimal currentValue =
                subcontract.getCurrentValue();

        BigDecimal remainingToInvoice =
                currentValue.subtract(invoicedAmount);

        BigDecimal outstandingInvoiceAmount =
                invoicedAmount
                        .subtract(paidAmount)
                        .subtract(deductedAmount);

        BigDecimal performanceSecurityDeductionAmount =
                calculateDeductionAmount(
                        subcontract,
                        subcontract.getPerformanceSecurityDeduction()
                );

        BigDecimal otherDeductionAmount =
                calculateDeductionAmount(
                        subcontract,
                        subcontract.getOtherDeduction()
                );

        BigDecimal totalContractualDeductionAmount =
                performanceSecurityDeductionAmount
                        .add(otherDeductionAmount);

        return SubcontractResponse.builder()
                .id(subcontract.getId())

                .mainContractId(
                        subcontract.getMainContract().getId()
                )
                .mainContractNumber(
                        subcontract.getMainContract()
                                .getContractNumber()
                )

                .investmentTaskId(
                        subcontract.getMainContract()
                                .getInvestmentTask()
                                .getId()
                )
                .investmentTaskNumber(
                        subcontract.getMainContract()
                                .getInvestmentTask()
                                .getTaskNumber()
                )

                .subcontractorId(
                        subcontract.getSubcontractor().getId()
                )
                .subcontractorName(
                        subcontract.getSubcontractor().getName()
                )
                .subcontractorTaxId(
                        subcontract.getSubcontractor().getTaxId()
                )

                .contractNumber(
                        subcontract.getContractNumber()
                )
                .contractDate(
                        subcontract.getContractDate()
                )

                .initialValue(
                        subcontract.getInitialValue()
                )
                .currentValue(
                        currentValue
                )
                .vatRate(
                        subcontract.getVatRate()
                )

                .initialStartDate(
                        subcontract.getInitialStartDate()
                )
                .initialEndDate(
                        subcontract.getInitialEndDate()
                )

                .currentStartDate(
                        subcontract.getCurrentStartDate()
                )
                .currentEndDate(
                        subcontract.getCurrentEndDate()
                )

                .contractType(
                        subcontract.getContractType()
                )
                .remunerationType(
                        subcontract.getRemunerationType()
                )

                .shortDescription(
                        subcontract.getShortDescription()
                )

                .totalPenaltyAmount(
                        subcontract.getTotalPenaltyAmount()
                )

                .invoicedAmount(
                        invoicedAmount
                )
                .remainingToInvoice(
                        remainingToInvoice
                )

                .paidAmount(
                        paidAmount
                )

                .deductedAmount(
                        deductedAmount
                )

                .performanceSecurityDeduction(
                        toDeductionRuleResponse(
                                subcontract
                                        .getPerformanceSecurityDeduction()
                        )
                )

                .otherDeduction(
                        toDeductionRuleResponse(
                                subcontract.getOtherDeduction()
                        )
                )

                .performanceSecurityDeductionAmount(
                        performanceSecurityDeductionAmount
                )

                .otherDeductionAmount(
                        otherDeductionAmount
                )

                .totalContractualDeductionAmount(
                        totalContractualDeductionAmount
                )

                .outstandingInvoiceAmount(
                        outstandingInvoiceAmount
                )

                .build();
    }

    private DeductionRuleResponse toDeductionRuleResponse(
            DeductionRule rule
    ) {
        if (rule == null) {
            return null;
        }

        if (rule.getPercentage() == null
                || rule.getBasis() == null) {
            return null;
        }

        return DeductionRuleResponse.builder()
                .percentage(rule.getPercentage())
                .basis(rule.getBasis())
                .build();
    }

    private BigDecimal calculateDeductionAmount(
            Subcontract subcontract,
            DeductionRule rule
    ) {
        if (rule == null
                || rule.getPercentage() == null
                || rule.getBasis() == null) {

            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount;

        if (rule.getBasis() == ContractValueBasis.NET) {
            baseAmount =
                    calculateCurrentNetValue(subcontract);
        } else {
            baseAmount =
                    subcontract.getCurrentValue();
        }

        return baseAmount
                .multiply(rule.getPercentage())
                .divide(
                        BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal calculateCurrentNetValue(
            Subcontract subcontract
    ) {
        BigDecimal vatMultiplier =
                BigDecimal.ONE.add(
                        subcontract.getVatRate()
                                .divide(
                                        BigDecimal.valueOf(100),
                                        10,
                                        RoundingMode.HALF_UP
                                )
                );

        return subcontract.getCurrentValue()
                .divide(
                        vatMultiplier,
                        2,
                        RoundingMode.HALF_UP
                );
    }
}