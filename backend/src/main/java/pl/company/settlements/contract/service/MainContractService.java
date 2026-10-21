package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.MainContractAdministration;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.MainContractCreateRequest;
import pl.company.settlements.contract.model.MainContractFilter;
import pl.company.settlements.contract.model.MainContractResponse;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.model.MainContractAdministrationResponse;
import pl.company.settlements.contract.model.SecurityReturnRequest;
import pl.company.settlements.contract.model.FinancialLimitPlanRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.specification.MainContractSpecification;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.contractor.service.ContractorService;
import pl.company.settlements.invoice.model.MainContractInvoicedAmount;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
import pl.company.settlements.payment.model.MainContractPaidAmount;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.repository.InvestmentTaskRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainContractService {

    private final MainContractRepository repository;
    private final InvestmentTaskRepository investmentTaskRepository;
    private final ContractorRepository contractorRepository;
    private final MainContractorInvoiceRepository mainContractorInvoiceRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final ContractorService contractorService;
    private final ContractHierarchyValidationService hierarchyValidationService;
    private final FinancialLimitService financialLimitService;

    public MainContractResponse getById(
            Long id
    ) {

        MainContract contract =
                findEntityById(id);

        BigDecimal invoicedAmount =
                mainContractorInvoiceRepository
                        .sumGrossAmountByMainContractId(
                                id
                        );

        BigDecimal paidAmount =
                paymentAllocationRepository
                        .sumPaidAmountByMainContractId(
                                id
                        );

        return toResponse(
                contract,
                invoicedAmount,
                paidAmount
        );
    }

    public List<MainContractResponse> findAll(
            MainContractFilter filter
    ) {

        List<MainContract> contracts =
                repository.findAll(
                        MainContractSpecification
                                .fromFilter(filter),

                        Sort.by(
                                Sort.Order.desc(
                                        "contractDate"
                                ),
                                Sort.Order.desc(
                                        "id"
                                )
                        )
                );

        if (contracts.isEmpty()) {
            return List.of();
        }

        List<Long> contractIds =
                contracts.stream()
                        .map(
                                MainContract::getId
                        )
                        .toList();

        Map<Long, BigDecimal> invoicedAmounts =
                mainContractorInvoiceRepository
                        .sumGrossAmountsByMainContractIds(
                                contractIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        MainContractInvoicedAmount
                                                ::getMainContractId,

                                        MainContractInvoicedAmount
                                                ::getInvoicedAmount
                                )
                        );

        Map<Long, BigDecimal> paidAmounts =
                paymentAllocationRepository
                        .sumPaidAmountsByMainContractIds(
                                contractIds
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        MainContractPaidAmount
                                                ::getMainContractId,

                                        MainContractPaidAmount
                                                ::getPaidAmount
                                )
                        );

        return contracts.stream()
                .map(contract -> {

                    BigDecimal invoicedAmount =
                            invoicedAmounts.getOrDefault(
                                    contract.getId(),
                                    BigDecimal.ZERO
                            );

                    BigDecimal paidAmount =
                            paidAmounts.getOrDefault(
                                    contract.getId(),
                                    BigDecimal.ZERO
                            );

                    return toResponse(
                            contract,
                            invoicedAmount,
                            paidAmount
                    );
                })
                .toList();
    }

    @Transactional
    public MainContractResponse create(
            MainContractCreateRequest request
    ) {
        MainContractAdministration administration = MainContractAdministrationRules.validatedValue(request.getAdministration());
        MainContractAdministrationRules.validateCompletionDate(request.getContractDate(),
                administration == null ? null : administration.getActualCompletionDate());
        FinancialLimitYearRules.validateInitialYears(request.getContractDate(), request.getFinancialLimitYears());
        InvestmentTask investmentTask = investmentTaskRepository
                .findById(request.getInvestmentTaskId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id "
                                + request.getInvestmentTaskId()
                                + " does not exist"
                ));

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
        );

        Contractor mainContractor = resolveMainContractor(request);

        MainContract contract = new MainContract(
                investmentTask,
                mainContractor
        );

        applyContractData(
                contract,
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

        contract.setAdministration(administration);
        MainContract savedContract = repository.save(contract);

        if (request.getFinancialLimitYears() != null) {
            // Ta sama transakcja: wykonawca, umowa i limity zatwierdzają się razem.
            // Serwis limitów blokuje budżet i zastępuje domyślne zaangażowanie nowym planem.
            financialLimitService.createInitialPlan(savedContract.getId(),
                    new FinancialLimitPlanRequest(request.getFinancialLimitYears()));
        }

        return toResponse(
                savedContract,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    @Transactional
    public MainContractResponse update(
            Long id,
            MainContractUpdateRequest request
    ) {
        MainContract contract = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Main contract with id " + id + " does not exist"));

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
        );

        MainContractAdministration administration = MainContractAdministrationRules.validatedValue(request.getAdministration());
        MainContractAdministration effectiveAdministration = request.getAdministration() == null
                ? contract.getAdministration() : administration;
        MainContractAdministrationRules.preserveReturns(contract.getAdministration(), administration);
        MainContractAdministrationRules.validateCompletionDate(request.getContractDate(),
                effectiveAdministration == null ? null : effectiveAdministration.getActualCompletionDate());
        if (contract.getFinancialLimit() != null) {
            contract.getFinancialLimit().getYears().stream().filter(year -> year.getAmount().signum() > 0)
                    .forEach(year -> FinancialLimitYearRules.validateYear(request.getContractDate(), year.getYear()));
        }
        hierarchyValidationService.validateMainContractUpdate(contract, request);

        applyContractData(
                contract,
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

        if (request.getAdministration() != null) contract.setAdministration(administration);

        BigDecimal invoicedAmount =
                mainContractorInvoiceRepository
                        .sumGrossAmountByMainContractId(id);

        BigDecimal paidAmount =
                paymentAllocationRepository
                        .sumPaidAmountByMainContractId(
                                id
                        );

        return toResponse(
                contract,
                invoicedAmount,
                paidAmount
        );
    }

    @Transactional
    public MainContractAdministrationResponse updateSecurityReturn(Long id, SecurityReturnRequest request) {
        if (request == null) throw new IllegalArgumentException("Podaj dane zwrotu zabezpieczenia.");
        MainContract contract = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Main contract with id " + id + " does not exist"));
        MainContractAdministrationRules.setReturned(contract.getAdministration(), request.stage(), request.returned());
        return MainContractAdministrationRules.toResponse(contract.getAdministration());
    }

    private MainContract findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Main contract with id "
                                + id
                                + " does not exist"
                ));
    }

    private Contractor resolveMainContractor(MainContractCreateRequest request) {
        if (!request.isMainContractorSelectionValid()) {
            throw new IllegalArgumentException(
                    "Wskaż istniejącego wykonawcę albo podaj dane nowego wykonawcy."
            );
        }

        if (request.getMainContractorId() != null) {
            return contractorRepository.findById(request.getMainContractorId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Contractor with id " + request.getMainContractorId()
                                    + " does not exist"
                    ));
        }

        // REQUIRED dołącza do transakcji create(): wykonawca i umowa
        // zatwierdzają się razem albo razem zostają wycofane.
        return contractorService.createEntity(request.getNewMainContractor());
    }

    private void applyContractData(
            MainContract contract,
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
        contract.setContractNumber(contractNumber.trim());
        contract.setContractDate(contractDate);
        contract.setInitialValue(initialValue);
        contract.setVatRate(vatRate);
        contract.setInitialStartDate(initialStartDate);
        contract.setInitialEndDate(initialEndDate);
        contract.setContractType(contractType);
        contract.setRemunerationType(remunerationType);
        contract.setShortDescription(
                trimToNull(shortDescription)
        );
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

    private MainContractResponse toResponse(
            MainContract contract,
            BigDecimal invoicedAmount,
            BigDecimal paidAmount
    ) {
        BigDecimal currentValue =
                contract.getCurrentValue();

        BigDecimal subcontractsTotalValue = contract.getSubcontracts().stream().filter(sub -> sub.getParentSubcontract() == null)
                .map(Subcontract::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingToInvoice =
                currentValue.subtract(
                        invoicedAmount
                );

        BigDecimal remainingToPay =
                invoicedAmount.subtract(
                        paidAmount
                );

        return MainContractResponse.builder()
                .administration(MainContractAdministrationRules.toResponse(contract.getAdministration()))
                .id(contract.getId())

                .investmentTaskId(
                        contract.getInvestmentTask().getId()
                )
                .investmentTaskNumber(
                        contract.getInvestmentTask().getTaskNumber()
                )

                .mainContractorId(
                        contract.getMainContractor().getId()
                )
                .mainContractorName(
                        contract.getMainContractor().getName()
                )
                .mainContractorTaxId(
                        contract.getMainContractor().getTaxId()
                )

                .contractNumber(
                        contract.getContractNumber()
                )
                .contractDate(
                        contract.getContractDate()
                )

                .initialValue(
                        contract.getInitialValue()
                )
                .currentValue(
                        currentValue
                )

                .subcontractsTotalValue(subcontractsTotalValue)
                .remainingForSubcontracts(currentValue.subtract(subcontractsTotalValue))

                .invoicedAmount(
                        invoicedAmount
                )
                .remainingToInvoice(
                        remainingToInvoice
                )

                .paidAmount(
                        paidAmount
                )
                .remainingToPay(
                        remainingToPay
                )

                .vatRate(
                        contract.getVatRate()
                )

                .initialStartDate(
                        contract.getInitialStartDate()
                )
                .initialEndDate(
                        contract.getInitialEndDate()
                )

                .currentStartDate(
                        contract.getCurrentStartDate()
                )
                .currentEndDate(
                        contract.getCurrentEndDate()
                )

                .contractType(
                        contract.getContractType()
                )
                .remunerationType(
                        contract.getRemunerationType()
                )

                .shortDescription(
                        contract.getShortDescription()
                )

                .totalPenaltyAmount(
                        contract.getTotalPenaltyAmount()
                )

                .build();
    }
}
