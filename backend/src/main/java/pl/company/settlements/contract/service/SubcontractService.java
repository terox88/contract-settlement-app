package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.ContractValueBasis;
import pl.company.settlements.contract.domain.DeductionRule;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.DeductionRuleRequest;
import pl.company.settlements.contract.model.DeductionRuleResponse;
import pl.company.settlements.contract.model.SubcontractCreateRequest;
import pl.company.settlements.contract.model.SubcontractFilter;
import pl.company.settlements.contract.model.SubcontractResponse;
import pl.company.settlements.contract.model.SubcontractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.SubcontractRepository;
import pl.company.settlements.contract.repository.specification.SubcontractSpecification;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.invoice.model.SubcontractInvoicedAmount;
import pl.company.settlements.invoice.repository.SubcontractorInvoiceRepository;
import pl.company.settlements.payment.model.SubcontractPaymentAmounts;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubcontractService {

    private final SubcontractRepository repository;
    private final MainContractRepository mainContractRepository;
    private final ContractorRepository contractorRepository;

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

    @Transactional
    public SubcontractResponse create(
            SubcontractCreateRequest request
    ) {
        MainContract mainContract =
                mainContractRepository
                        .findById(request.getMainContractId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Main contract with id "
                                                + request.getMainContractId()
                                                + " does not exist"
                                )
                        );

        Contractor subcontractor =
                contractorRepository
                        .findById(request.getSubcontractorId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Contractor with id "
                                                + request.getSubcontractorId()
                                                + " does not exist"
                                )
                        );

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
        );

        Subcontract subcontract =
                new Subcontract(
                        mainContract,
                        subcontractor
                );

        applyContractData(
                subcontract,
                request.getContractNumber(),
                request.getContractDate(),
                request.getInitialValue(),
                request.getVatRate(),
                request.getInitialStartDate(),
                request.getInitialEndDate(),
                request.getContractType(),
                request.getRemunerationType(),
                request.getShortDescription()
        );

        subcontract.setPerformanceSecurityDeduction(
                toDeductionRule(
                        request.getPerformanceSecurityDeduction()
                )
        );

        subcontract.setOtherDeduction(
                toDeductionRule(
                        request.getOtherDeduction()
                )
        );

        Subcontract savedSubcontract =
                repository.save(subcontract);

        return toResponse(
                savedSubcontract,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    @Transactional
    public SubcontractResponse update(
            Long id,
            SubcontractUpdateRequest request
    ) {
        Subcontract subcontract = findEntityById(id);

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
        );

        applyContractData(
                subcontract,
                request.getContractNumber(),
                request.getContractDate(),
                request.getInitialValue(),
                request.getVatRate(),
                request.getInitialStartDate(),
                request.getInitialEndDate(),
                request.getContractType(),
                request.getRemunerationType(),
                request.getShortDescription()
        );

        subcontract.setPerformanceSecurityDeduction(
                toDeductionRule(
                        request.getPerformanceSecurityDeduction()
                )
        );

        subcontract.setOtherDeduction(
                toDeductionRule(
                        request.getOtherDeduction()
                )
        );

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

    private Subcontract findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Subcontract with id "
                                        + id
                                        + " does not exist"
                        )
                );
    }

    private void applyContractData(
            Subcontract subcontract,
            String contractNumber,
            LocalDate contractDate,
            BigDecimal initialValue,
            BigDecimal vatRate,
            LocalDate initialStartDate,
            LocalDate initialEndDate,
            ContractType contractType,
            RemunerationType remunerationType,
            String shortDescription
    ) {
        subcontract.setContractNumber(
                contractNumber.trim()
        );

        subcontract.setContractDate(
                contractDate
        );

        subcontract.setInitialValue(
                initialValue
        );

        subcontract.setVatRate(
                vatRate
        );

        subcontract.setInitialStartDate(
                initialStartDate
        );

        subcontract.setInitialEndDate(
                initialEndDate
        );

        subcontract.setContractType(
                contractType
        );

        subcontract.setRemunerationType(
                remunerationType
        );

        subcontract.setShortDescription(
                trimToNull(shortDescription)
        );
    }

    private DeductionRule toDeductionRule(
            DeductionRuleRequest request
    ) {
        if (request == null) {
            return null;
        }

        DeductionRule rule = new DeductionRule();

        rule.setPercentage(
                request.getPercentage()
        );

        rule.setBasis(
                request.getBasis()
        );

        return rule;
    }

    private void validateDates(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {

            throw new IllegalArgumentException(
                    "Contract end date cannot be before start date"
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
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
        if (rule == null
                || rule.getPercentage() == null
                || rule.getBasis() == null) {

            return null;
        }

        return DeductionRuleResponse.builder()
                .percentage(
                        rule.getPercentage()
                )
                .basis(
                        rule.getBasis()
                )
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
                    calculateCurrentNetValue(
                            subcontract
                    );
        } else {
            baseAmount =
                    subcontract.getCurrentValue();
        }

        return baseAmount
                .multiply(
                        rule.getPercentage()
                )
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