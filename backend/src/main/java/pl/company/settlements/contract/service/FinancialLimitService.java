package pl.company.settlements.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.company.settlements.contract.domain.FinancialLimit;
import pl.company.settlements.contract.domain.FinancialLimitYear;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.model.FinancialLimitAmountRequest;
import pl.company.settlements.contract.model.FinancialLimitCreateRequest;
import pl.company.settlements.contract.model.FinancialLimitResponse;
import pl.company.settlements.contract.model.FinancialLimitYearRequest;
import pl.company.settlements.contract.model.FinancialLimitYearResponse;
import pl.company.settlements.contract.repository.FinancialLimitRepository;
import pl.company.settlements.contract.repository.MainContractRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialLimitService {

    private final FinancialLimitRepository financialLimitRepository;
    private final MainContractRepository mainContractRepository;

    public FinancialLimitResponse getByMainContractId(
            Long mainContractId
    ) {
        return toResponse(
                findEntityByMainContractId(mainContractId)
        );
    }

    @Transactional
    public FinancialLimitResponse create(
            FinancialLimitCreateRequest request
    ) {
        Long mainContractId = request.getMainContractId();

        if (financialLimitRepository
                .existsByMainContract_Id(mainContractId)) {

            throw new IllegalArgumentException(
                    "Financial limit for main contract with id "
                            + mainContractId
                            + " already exists"
            );
        }

        MainContract mainContract = mainContractRepository
                .findById(mainContractId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Main contract with id "
                                + mainContractId
                                + " does not exist"
                ));

        FinancialLimit financialLimit =
                mainContract.getOrCreateFinancialLimit();

        for (FinancialLimitYearRequest year : request.getYears()) {
            financialLimit.addYear(
                    year.getYear(),
                    year.getAmount()
            );
        }

        FinancialLimit savedLimit =
                financialLimitRepository.save(financialLimit);

        return toResponse(savedLimit);

    }

    @Transactional
    public FinancialLimitResponse addYear(
            Long mainContractId,
            FinancialLimitYearRequest request
    ) {
        FinancialLimit financialLimit =
                findEntityByMainContractId(mainContractId);

        financialLimit.addYear(
                request.getYear(),
                request.getAmount()
        );

        return toResponse(financialLimit);
    }

    @Transactional
    public FinancialLimitResponse changeAmountForYear(
            Long mainContractId,
            int year,
            FinancialLimitAmountRequest request
    ) {
        FinancialLimit financialLimit =
                findEntityByMainContractId(mainContractId);

        financialLimit.changeAmountForYear(
                year,
                request.getAmount()
        );

        return toResponse(financialLimit);
    }

    private FinancialLimit findEntityByMainContractId(
            Long mainContractId
    ) {
        return financialLimitRepository
                .findByMainContract_Id(mainContractId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Financial limit for main contract with id "
                                + mainContractId
                                + " does not exist"
                ));
    }

    private FinancialLimitResponse toResponse(
            FinancialLimit financialLimit
    ) {
        MainContract mainContract =
                financialLimit.getMainContract();

        return FinancialLimitResponse.builder()
                .id(financialLimit.getId())

                .mainContractId(
                        mainContract.getId()
                )
                .contractNumber(
                        mainContract.getContractNumber()
                )

                .currentContractValue(
                        mainContract.getCurrentValue()
                )

                .years(
                        financialLimit.getYears()
                                .stream()
                                .map(this::toYearResponse)
                                .toList()
                )

                .totalAmount(
                        financialLimit.getTotalAmount()
                )

                .build();
    }

    private FinancialLimitYearResponse toYearResponse(
            FinancialLimitYear year
    ) {
        return FinancialLimitYearResponse.builder()
                .id(year.getId())
                .year(year.getYear())
                .amount(year.getAmount())
                .build();
    }
}