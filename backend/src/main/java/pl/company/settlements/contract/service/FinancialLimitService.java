package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.budget.domain.Budget;
import pl.company.settlements.budget.domain.BudgetYear;
import pl.company.settlements.budget.repository.BudgetRepository;
import pl.company.settlements.budget.service.BudgetCommitmentService;
import pl.company.settlements.common.error.ConflictException;
import pl.company.settlements.common.error.ResourceNotFoundException;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.FinancialLimitYear;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.model.FinancialLimitAmountRequest;
import pl.company.settlements.contract.model.FinancialLimitCreateRequest;
import pl.company.settlements.contract.model.FinancialLimitPlanRequest;
import pl.company.settlements.contract.model.FinancialLimitResponse;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.model.FinancialLimitYearResponse;
import pl.company.settlements.contract.repository.FinancialLimitRepository;
import pl.company.settlements.contract.repository.MainContractRepository;
import pl.company.settlements.payment.model.MainContractPaymentByDate;
import pl.company.settlements.payment.repository.PaymentAllocationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialLimitService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final FinancialLimitRepository financialLimitRepository;
    private final MainContractRepository mainContractRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetCommitmentService budgetCommitmentService;
    private final PaymentAllocationRepository paymentAllocationRepository;

    public FinancialLimitResponse getByMainContractId(Long mainContractId) {
        return financialLimitRepository.findByMainContract_Id(mainContractId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Financial limits for main contract " + mainContractId
                                + " have not been defined"
                ));
    }

    @Transactional
    public FinancialLimitResponse replacePlan(
            Long mainContractId, FinancialLimitPlanRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Financial limit plan is required");
        }
        return modifyPlan(mainContractId, old -> parsePlan(request.years()));
    }

    @Transactional
    public FinancialLimitResponse create(FinancialLimitCreateRequest request) {
        if (request == null || request.getMainContractId() == null) {
            throw new IllegalArgumentException("Main contract id is required");
        }
        return modifyPlan(request.getMainContractId(), old -> {
            if (old != null) {
                throw new ConflictException("Financial limits already exist for this main contract");
            }
            return parsePlan(request.getYears());
        });
    }

    @Transactional
    public FinancialLimitResponse addYear(
            Long mainContractId, FinancialLimitYearRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Financial limit year is required");
        }
        return modifyPlan(mainContractId, old -> {
            if (old == null) {
                throw new ResourceNotFoundException("Financial limits have not been defined");
            }
            Map<Integer, BigDecimal> next = currentPlan(old);
            int year = request.getYear();
            if (next.containsKey(year)) {
                throw new IllegalArgumentException("Financial limit for year " + year + " already exists");
            }
            next.put(year, request.getAmount());
            return next;
        });
    }

    @Transactional
    public FinancialLimitResponse changeAmountForYear(
            Long mainContractId, int year, FinancialLimitAmountRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Financial limit amount is required");
        }
        return modifyPlan(mainContractId, old -> {
            if (old == null) {
                throw new ResourceNotFoundException("Financial limits have not been defined");
            }
            Map<Integer, BigDecimal> next = currentPlan(old);
            if (!next.containsKey(year)) {
                throw new IllegalArgumentException("Financial limit for year " + year + " does not exist");
            }
            next.put(year, request.getAmount());
            return next;
        });
    }

    /**
     * All write operations use one budget-row lock, validate the complete
     * resulting plan, and then perform one JPA persistence operation.
     */
    private FinancialLimitResponse modifyPlan(
            Long mainContractId,
            Function<FinancialLimit, Map<Integer, BigDecimal>> change
    ) {
        MainContract contract = mainContractRepository.findById(mainContractId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Main contract " + mainContractId + " does not exist"
                ));

        Long taskId = contract.getInvestmentTask().getId();
        Budget budget = budgetRepository.findByInvestmentTaskIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Investment task " + taskId + " has no budget"
                ));

        FinancialLimit existing = financialLimitRepository
                .findByMainContract_Id(mainContractId)
                .orElse(null);

        Map<Integer, BigDecimal> proposed = change.apply(existing);
        validatePlan(proposed, existing, contract, budget);

        FinancialLimit entity = existing == null
                ? contract.getOrCreateFinancialLimit()
                : existing;
        entity.replaceYears(proposed);
        return toResponse(financialLimitRepository.saveAndFlush(entity));
    }

    private Map<Integer, BigDecimal> parsePlan(List<FinancialLimitYearRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Specify at least one financial limit year");
        }
        Map<Integer, BigDecimal> plan = new LinkedHashMap<>();
        for (FinancialLimitYearRequest row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("Financial limit year cannot be null");
            }
            if (plan.putIfAbsent(row.getYear(), row.getAmount()) != null) {
                throw new IllegalArgumentException("Duplicate financial limit year: " + row.getYear());
            }
        }
        return plan;
    }

    private Map<Integer, BigDecimal> currentPlan(FinancialLimit limit) {
        Map<Integer, BigDecimal> plan = new LinkedHashMap<>();
        for (FinancialLimitYear year : limit.getYears()) {
            plan.put(year.getYear(), year.getAmount());
        }
        return plan;
    }

    private Map<Integer, BigDecimal> oldCommitment(
            FinancialLimit existing, MainContract contract
    ) {
        if (existing == null || existing.getYears().isEmpty()) {
            return Map.of(contract.getContractDate().getYear(), contract.getCurrentValue());
        }
        return currentPlan(existing);
    }

    private void validatePlan(
            Map<Integer, BigDecimal> proposed,
            FinancialLimit existing,
            MainContract contract,
            Budget budget
    ) {
        if (proposed == null || proposed.isEmpty()) {
            throw new IllegalArgumentException("Financial limit plan cannot be empty");
        }

        BigDecimal total = ZERO;
        for (Map.Entry<Integer, BigDecimal> entry : proposed.entrySet()) {
            int year = entry.getKey();
            BigDecimal amount = entry.getValue();
            if (year < 2000 || year > 2200) {
                throw new IllegalArgumentException("Invalid financial limit year: " + year);
            }
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Financial limit for " + year + " must be nonnegative");
            }
            try {
                BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
                if (normalized.precision() > 15) {
                    throw new IllegalArgumentException("Financial limit for " + year + " is too large");
                }
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Use a maximum of two decimal places for " + year);
            }
            total = total.add(amount);
        }

        if (total.compareTo(contract.getCurrentValue()) != 0) {
            throw new IllegalArgumentException(
                    "Sum of financial limits (" + total
                            + ") must equal current contract value ("
                            + contract.getCurrentValue() + ")"
            );
        }

        Map<Integer, BigDecimal> paidByYear = new HashMap<>();
        List<MainContractPaymentByDate> payments = paymentAllocationRepository
                .sumMainContractPaymentsByDate(contract.getId());
        for (MainContractPaymentByDate payment : payments) {
            if (!"PLN".equalsIgnoreCase(payment.getCurrency())) {
                throw new ConflictException(
                        "Cannot verify annual PLN limits while the contract has payments in another currency"
                );
            }
            if (payment.getPaymentDate() == null) {
                throw new ConflictException("Payment date is required for limit validation");
            }
            paidByYear.merge(payment.getPaymentDate().getYear(),
                    payment.getPaidAmount(), BigDecimal::add);
        }
        for (Map.Entry<Integer, BigDecimal> payment : paidByYear.entrySet()) {
            BigDecimal limit = proposed.getOrDefault(payment.getKey(), ZERO);
            if (limit.compareTo(payment.getValue()) < 0) {
                throw new ConflictException(
                        "Limit for " + payment.getKey() + " (" + limit
                                + ") is below already paid amount (" + payment.getValue() + ")"
                );
            }
        }

        Map<Integer, BigDecimal> annualBudget = budget.getYears().stream()
                .collect(Collectors.toMap(BudgetYear::getYear, BudgetYear::getAmount));
        for (Map.Entry<Integer, BigDecimal> entry : proposed.entrySet()) {
            if (entry.getValue().signum() > 0 && !annualBudget.containsKey(entry.getKey())) {
                throw new ConflictException("No budget defined for year " + entry.getKey());
            }
        }

        Map<Integer, BigDecimal> commitments = budgetCommitmentService
                .calculateByYear(contract.getInvestmentTask().getId());
        Map<Integer, BigDecimal> before = oldCommitment(existing, contract);

        Set<Integer> relevantYears = new HashSet<>(before.keySet());
        relevantYears.addAll(proposed.keySet());
        for (int year : relevantYears) {
            BigDecimal oldAmount = commitments.getOrDefault(year, ZERO);
            BigDecimal adjusted = oldAmount
                    .subtract(before.getOrDefault(year, ZERO))
                    .add(proposed.getOrDefault(year, ZERO));
            BigDecimal allowed = annualBudget.getOrDefault(year, ZERO);

            // Do not worsen an existing overcommitment; allow reducing it.
            if (adjusted.compareTo(allowed) > 0 && adjusted.compareTo(oldAmount) > 0) {
                throw new ConflictException(
                        "Insufficient budget in " + year + ": commitment would be "
                                + adjusted + ", budget is " + allowed
                );
            }
        }
    }

    private FinancialLimitResponse toResponse(FinancialLimit limit) {
        MainContract contract = limit.getMainContract();
        return FinancialLimitResponse.builder()
                .id(limit.getId())
                .mainContractId(contract.getId())
                .contractNumber(contract.getContractNumber())
                .currentContractValue(contract.getCurrentValue())
                .years(limit.getYears().stream().map(this::toYearResponse).toList())
                .totalAmount(limit.getTotalAmount())
                .build();
    }

    private FinancialLimitYearResponse toYearResponse(FinancialLimitYear year) {
        return FinancialLimitYearResponse.builder()
                .id(year.getId())
                .year(year.getYear())
                .amount(year.getAmount())
                .build();
    }
}
