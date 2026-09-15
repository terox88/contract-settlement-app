package pl.company.settlements.contract.repository.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.MainContract;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.model.MainContractFilter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public final class MainContractSpecification {

    private MainContractSpecification() {
    }

    public static Specification<MainContract> fromFilter(
            MainContractFilter filter
    ) {
        return Specification.allOf(
                contractNumberContains(filter.getContractNumber()),
                shortDescriptionContains(filter.getShortDescription()),
                hasInvestmentTask(filter.getInvestmentTaskId()),
                hasMainContractor(filter.getMainContractorId()),
                hasContractType(filter.getContractType()),
                hasRemunerationType(filter.getRemunerationType()),
                contractDateFrom(filter.getContractDateFrom()),
                contractDateTo(filter.getContractDateTo()),
                currentValueFrom(filter.getCurrentValueFrom()),
                currentValueTo(filter.getCurrentValueTo())
        );
    }

    private static Specification<MainContract> contractNumberContains(
            String contractNumber
    ) {
        if (contractNumber == null || contractNumber.isBlank()) {
            return Specification.unrestricted();
        }

        String pattern =
                "%" + contractNumber.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("contractNumber")),
                        pattern
                );
    }

    private static Specification<MainContract> shortDescriptionContains(
            String description
    ) {
        if (description == null || description.isBlank()) {
            return Specification.unrestricted();
        }

        String pattern =
                "%" + description.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("shortDescription")),
                        pattern
                );
    }

    private static Specification<MainContract> hasInvestmentTask(
            Long investmentTaskId
    ) {
        if (investmentTaskId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("investmentTask").get("id"),
                        investmentTaskId
                );
    }

    private static Specification<MainContract> hasMainContractor(
            Long mainContractorId
    ) {
        if (mainContractorId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("mainContractor").get("id"),
                        mainContractorId
                );
    }

    private static Specification<MainContract> hasContractType(
            ContractType contractType
    ) {
        if (contractType == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("contractType"), contractType);
    }

    private static Specification<MainContract> hasRemunerationType(
            RemunerationType remunerationType
    ) {
        if (remunerationType == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("remunerationType"),
                        remunerationType
                );
    }

    private static Specification<MainContract> contractDateFrom(
            LocalDate date
    ) {
        if (date == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(
                        root.get("contractDate"),
                        date
                );
    }

    private static Specification<MainContract> contractDateTo(
            LocalDate date
    ) {
        if (date == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.lessThanOrEqualTo(
                        root.get("contractDate"),
                        date
                );
    }

    private static Specification<MainContract> currentValueFrom(
            BigDecimal value
    ) {
        if (value == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(
                        currentValue(root, query, cb),
                        value
                );
    }

    private static Specification<MainContract> currentValueTo(
            BigDecimal value
    ) {
        if (value == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.lessThanOrEqualTo(
                        currentValue(root, query, cb),
                        value
                );
    }

    private static Expression<BigDecimal> currentValue(
            jakarta.persistence.criteria.Root<MainContract> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        Subquery<BigDecimal> subquery =
                query.subquery(BigDecimal.class);

        var amendment = subquery.from(ContractAmendment.class);

        subquery.select(
                cb.coalesce(
                        cb.sum(
                                amendment.<BigDecimal>get("valueChange")
                        ),
                        BigDecimal.ZERO
                )
        );

        subquery.where(
                cb.equal(
                        amendment.get("contract").get("id"),
                        root.get("id")
                )
        );

        return cb.sum(
                root.<BigDecimal>get("initialValue"),
                subquery
        );
    }
}