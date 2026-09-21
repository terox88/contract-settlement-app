package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
                .filter(limitYear -> limitYear.getYear() == year)
                .map(FinancialLimitYear::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public void addYear(int year, BigDecimal amount) {
        boolean yearAlreadyExists = years.stream()
                .anyMatch(limitYear -> limitYear.getYear() == year);

        if (yearAlreadyExists) {
            throw new IllegalArgumentException(
                    "Financial limit for year " + year + " already exists"
            );
        }

        years.add(new FinancialLimitYear(this, year, amount));
    }

    public Long getId() {
        return id;
    }

    public MainContract getMainContract() {
        return mainContract;
    }

    public List<FinancialLimitYear> getYears() {
        return years;
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
}