package pl.company.settlements.invoice.repository.specification;

import org.springframework.data.jpa.domain.Specification;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.invoice.model.SubcontractorInvoiceFilter;
import pl.company.settlements.payment.domain.PaymentAllocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public final class SubcontractorInvoiceSpecification {

    private SubcontractorInvoiceSpecification() {
    }

    public static Specification<SubcontractorInvoice> fromFilter(
            SubcontractorInvoiceFilter filter
    ) {
        return Specification.allOf(
                invoiceNumberContains(filter.getInvoiceNumber()),
                hasSubcontract(filter.getSubcontractId()),
                hasSubcontractor(filter.getSubcontractorId()),
                issueDateFrom(filter.getIssueDateFrom()),
                issueDateTo(filter.getIssueDateTo()),
                grossAmountFrom(filter.getGrossAmountFrom()),
                settled(filter.getSettled()),
                grossAmountTo(filter.getGrossAmountTo()),
                hasStatement(filter.getHasStatement()),
                assignedToMainInvoice(filter.getAssignedToMainInvoice())
        );
    }

    private static Specification<SubcontractorInvoice> invoiceNumberContains(
            String invoiceNumber
    ) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return Specification.unrestricted();
        }

        String pattern = "%" + invoiceNumber.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("invoiceNumber")),
                        pattern
                );
    }

    private static Specification<SubcontractorInvoice> hasSubcontract(
            Long subcontractId
    ) {
        if (subcontractId == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("subcontract").get("id"),
                        subcontractId
                );
    }

    private static Specification<SubcontractorInvoice> hasSubcontractor(
            Long subcontractorId
    ) {
        if (subcontractorId == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("subcontract")
                                .get("subcontractor")
                                .get("id"),
                        subcontractorId
                );
    }

    private static Specification<SubcontractorInvoice> issueDateFrom(
            LocalDate date
    ) {
        if (date == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(
                        root.get("issueDate"),
                        date
                );
    }

    private static Specification<SubcontractorInvoice> issueDateTo(
            LocalDate date
    ) {
        if (date == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("issueDate"),
                        date
                );
    }

    private static Specification<SubcontractorInvoice> grossAmountFrom(
            BigDecimal amount
    ) {
        if (amount == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.greaterThanOrEqualTo(
                        root.get("grossAmount"),
                        amount
                );
    }

    private static Specification<SubcontractorInvoice> grossAmountTo(
            BigDecimal amount
    ) {
        if (amount == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("grossAmount"),
                        amount
                );
    }

    private static Specification<SubcontractorInvoice> hasStatement(
            Boolean hasStatement
    ) {
        if (hasStatement == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) -> {
            if (hasStatement) {
                return criteriaBuilder.isNotNull(
                        root.get("statement")
                );
            }

            return criteriaBuilder.isNull(
                    root.get("statement")
            );
        };
    }

    private static Specification<SubcontractorInvoice> assignedToMainInvoice(
            Boolean assigned
    ) {
        if (assigned == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) -> {
            if (assigned) {
                return criteriaBuilder.isNotEmpty(
                        root.get("mainInvoiceSettlements")
                );
            }

            return criteriaBuilder.isEmpty(
                    root.get("mainInvoiceSettlements")
            );
        };
    }

    private static Specification<SubcontractorInvoice> settled(
            Boolean settled
    ) {
        if (settled == null) {
            return Specification.unrestricted();
        }

        return (root, query, criteriaBuilder) -> {

            var subquery = query.subquery(BigDecimal.class);
            var allocation = subquery.from(
                    PaymentAllocation.class
            );

            var allocatedAmount = criteriaBuilder.coalesce(
                    criteriaBuilder.sum(
                            allocation.<BigDecimal>get("allocatedAmount")
                    ),
                    BigDecimal.ZERO
            );

            var deductedAmount = criteriaBuilder.coalesce(
                    criteriaBuilder.sum(
                            allocation.<BigDecimal>get("deductedAmount")
                    ),
                    BigDecimal.ZERO
            );

            subquery.select(
                    criteriaBuilder.sum(
                            allocatedAmount,
                            deductedAmount
                    )
            );

            subquery.where(
                    criteriaBuilder.equal(
                            allocation.get("subcontractorInvoice").get("id"),
                            root.get("id")
                    )
            );

            if (settled) {
                return criteriaBuilder.greaterThanOrEqualTo(
                        subquery,
                        root.<BigDecimal>get("grossAmount")
                );
            }

            return criteriaBuilder.lessThan(
                    subquery,
                    root.<BigDecimal>get("grossAmount")
            );
        };
    }
}