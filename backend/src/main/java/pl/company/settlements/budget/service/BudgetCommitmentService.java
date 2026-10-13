package pl.company.settlements.budget.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.FinancialLimitYear;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.repository.FinancialLimitRepository;
import pl.company.settlements.contract.repository.MainContractRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetCommitmentService {

    private final MainContractRepository mainContractRepository;
    private final FinancialLimitRepository financialLimitRepository;

    public Map<Integer, BigDecimal> calculateByYear(
            Long investmentTaskId
    ) {

        List<MainContract> contracts =
                mainContractRepository
                        .findAllByInvestmentTask_Id(
                                investmentTaskId
                        );

        List<FinancialLimit> financialLimits =
                financialLimitRepository
                        .findAllWithYearsByInvestmentTaskId(
                                investmentTaskId
                        );

        Map<Long, FinancialLimit> limitsByContract =
                financialLimits.stream()
                        .collect(Collectors.toMap(
                                limit -> limit.getMainContract().getId(),
                                Function.identity()
                        ));

        Map<Integer, BigDecimal> committedByYear =
                new HashMap<>();

        for (MainContract contract : contracts) {

            FinancialLimit limit =
                    limitsByContract.get(contract.getId());

            if (limit == null || limit.getYears().isEmpty()) {

                int contractYear =
                        contract.getContractDate().getYear();

                committedByYear.merge(
                        contractYear,
                        contract.getCurrentValue(),
                        BigDecimal::add
                );

            } else {

                for (FinancialLimitYear year : limit.getYears()) {

                    committedByYear.merge(
                            year.getYear(),
                            year.getAmount(),
                            BigDecimal::add
                    );
                }
            }
        }

        return committedByYear;
    }
}