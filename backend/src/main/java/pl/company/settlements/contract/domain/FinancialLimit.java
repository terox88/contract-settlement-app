package pl.company.settlements.contract.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Getter
@Entity
@Table(name = "financial_limit")
public class FinancialLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contract_id", nullable = false, unique = true)
    private MainContract mainContract;

    @OneToMany(
            mappedBy = "financialLimit",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("year ASC")
    private List<FinancialLimitYear> years = new ArrayList<>();

    protected FinancialLimit() {
    }

    public FinancialLimit(MainContract mainContract) {
        this.mainContract = mainContract;
    }

    public BigDecimal getTotalAmount() {
        return years.stream()
                .map(FinancialLimitYear::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAmountForYear(int year) {
        return years.stream()
                .filter(item -> item.getYear() == year)
                .map(FinancialLimitYear::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public void addYear(int year, BigDecimal amount) {
        if (years.stream().anyMatch(item -> item.getYear() == year)) {
            throw new IllegalArgumentException(
                    "Financial limit for year " + year + " already exists"
            );
        }
        years.add(new FinancialLimitYear(this, year, amount));
    }

    public void changeAmountForYear(int year, BigDecimal amount) {
        FinancialLimitYear limitYear = years.stream()
                .filter(item -> item.getYear() == year)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Financial limit for year " + year + " does not exist"
                ));
        limitYear.changeAmount(amount);
    }

    /**
     * Reconciles managed JPA children instead of clear()+reinsert().
     * Keeps existing rows for unchanged years and respects orphanRemoval.
     * Called only after all plan validations have succeeded.
     */
    public void replaceYears(Map<Integer, BigDecimal> newPlan) {
        years.removeIf(year -> !newPlan.containsKey(year.getYear()));

        for (Map.Entry<Integer, BigDecimal> entry : newPlan.entrySet()) {
            FinancialLimitYear existing = years.stream()
                    .filter(year -> year.getYear() == entry.getKey())
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                years.add(new FinancialLimitYear(this, entry.getKey(), entry.getValue()));
            } else {
                existing.changeAmount(entry.getValue());
            }
        }
        years.sort(Comparator.comparingInt(FinancialLimitYear::getYear));
    }
}
