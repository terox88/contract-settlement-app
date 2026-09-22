package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.model.MainContractCreateRequest;
import pl.company.settlements.contract.model.MainContractFilter;
import pl.company.settlements.contract.model.MainContractResponse;
import pl.company.settlements.contract.model.MainContractUpdateRequest;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.contract.repository.specification.MainContractSpecification;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.contractor.repository.ContractorRepository;
import pl.company.settlements.invoice.model.MainContractInvoicedAmount;
import pl.company.settlements.invoice.repository.MainContractorInvoiceRepository;
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

    public MainContractResponse getById(Long id) {
        MainContract contract = findEntityById(id);

        BigDecimal invoicedAmount =
                mainContractorInvoiceRepository
                        .sumGrossAmountByMainContractId(id);

        return toResponse(
                contract,
                invoicedAmount
        );
    }

    public List<MainContractResponse> findAll(
            MainContractFilter filter
    ) {
        List<MainContract> contracts = repository.findAll(
                MainContractSpecification.fromFilter(filter),
                Sort.by(
                        Sort.Order.desc("contractDate"),
                        Sort.Order.desc("id")
                )
        );

        if (contracts.isEmpty()) {
            return List.of();
        }

        List<Long> contractIds = contracts.stream()
                .map(MainContract::getId)
                .toList();

        Map<Long, BigDecimal> invoicedAmounts =
                mainContractorInvoiceRepository
                        .sumGrossAmountsByMainContractIds(contractIds)
                        .stream()
                        .collect(Collectors.toMap(
                                MainContractInvoicedAmount::getMainContractId,
                                MainContractInvoicedAmount::getInvoicedAmount
                        ));

        return contracts.stream()
                .map(contract -> toResponse(
                        contract,
                        invoicedAmounts.getOrDefault(
                                contract.getId(),
                                BigDecimal.ZERO
                        )
                ))
                .toList();
    }

    @Transactional
    public MainContractResponse create(
            MainContractCreateRequest request
    ) {
        InvestmentTask investmentTask = investmentTaskRepository
                .findById(request.getInvestmentTaskId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task with id "
                                + request.getInvestmentTaskId()
                                + " does not exist"
                ));

        Contractor mainContractor = contractorRepository
                .findById(request.getMainContractorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Contractor with id "
                                + request.getMainContractorId()
                                + " does not exist"
                ));

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
        );

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

        MainContract savedContract = repository.save(contract);

        return toResponse(
                savedContract,
                BigDecimal.ZERO
        );
    }

    @Transactional
    public MainContractResponse update(
            Long id,
            MainContractUpdateRequest request
    ) {
        MainContract contract = findEntityById(id);

        validateDates(
                request.getInitialStartDate(),
                request.getInitialEndDate()
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

        BigDecimal invoicedAmount =
                mainContractorInvoiceRepository
                        .sumGrossAmountByMainContractId(id);

        return toResponse(
                contract,
                invoicedAmount
        );
    }

    private MainContract findEntityById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Main contract with id "
                                + id
                                + " does not exist"
                ));
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
            BigDecimal invoicedAmount
    ) {
        BigDecimal currentValue =
                contract.getCurrentValue();

        BigDecimal remainingToInvoice =
                currentValue.subtract(invoicedAmount);

        return MainContractResponse.builder()
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

                .invoicedAmount(
                        invoicedAmount
                )
                .remainingToInvoice(
                        remainingToInvoice
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