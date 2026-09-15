package pl.company.settlements.contract.repository.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import pl.company.settlements.contract.domain.ContractAmendment;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.domain.RemunerationType;
import pl.company.settlements.contract.domain.Subcontract;
import pl.company.settlements.contract.model.SubcontractFilter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public final class SubcontractSpecification {

    private SubcontractSpecification() {
    }

    public static Specification<Subcontract> fromFilter(
            SubcontractFilter filter
    ) {
        return Specification.allOf(
                contractNumberContains(filter.getContractNumber()),
                shortDescriptionContains(filter.getShortDescription()),
                hasSubcontractor(filter.getSubcontractorId()),
                hasMainContract(filter.getMainContractId()),
                hasInvestmentTask(filter.getInvestmentTaskId()),
                hasContractType(filter.getContractType()),
                hasRemunerationType(filter.getRemunerationType()),
                contractDateFrom(filter.getContractDateFrom()),
                contractDateTo(filter.getContractDateTo()),
                currentValueFrom(filter.getCurrentValueFrom()),
                currentValueTo(filter.getCurrentValueTo())
        );
    }

    private static Specification<Subcontract> contractNumberContains(
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

    private static Specification<Subcontract> shortDescriptionContains(
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

    private static Specification<Subcontract> hasSubcontractor(
            Long subcontractorId
    ) {
        if (subcontractorId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("subcontractor").get("id"),
                        subcontractorId
                );
    }

    private static Specification<Subcontract> hasMainContract(
            Long mainContractId
    ) {
        if (mainContractId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("mainContract").get("id"),
                        mainContractId
                );
    }

    private static Specification<Subcontract> hasInvestmentTask(
            Long investmentTaskId
    ) {
        if (investmentTaskId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(
                        root.get("mainContract")
                                .get("investmentTask")
                                .get("id"),
                        investmentTaskId
                );
    }

    private static Specification<Subcontract> hasContractType(
            ContractType contractType
    ) {
        if (contractType == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("contractType"), contractType);
    }

    private static Specification<Subcontract> hasRemunerationType(
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

    private static Specification<Subcontract> contractDateFrom(
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

    private static Specification<Subcontract> contractDateTo(
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

    private static Specification<Subcontract> currentValueFrom(
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

    private static Specification<Subcontract> currentValueTo(
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
            jakarta.persistence.criteria.Root<Subcontract> root,
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