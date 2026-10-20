package pl.company.settlements.task.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pl.company.settlements.task.domain.InvestmentTask;
import pl.company.settlements.task.domain.InvestmentTaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InvestmentTaskSpecification {
    private InvestmentTaskSpecification() {}

    public static Specification<InvestmentTask> fromFilters(InvestmentTaskStatus status, String implementer) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (implementer != null && !implementer.isBlank()) {
                String escaped = implementer.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(cb.lower(root.get("implementer")), "%" + escaped + "%", '!'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
