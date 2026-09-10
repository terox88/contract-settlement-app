package pl.company.settlements.budget.domain;

import jakarta.persistence.*;
import pl.company.settlements.task.domain.InvestmentTask;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "budget")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_task_id", nullable = false, unique = true)
    private InvestmentTask investmentTask;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("year ASC")
    private List<BudgetYear> years = new ArrayList<>();

    protected Budget() {
    }

    public Budget(InvestmentTask investmentTask) {
        this.investmentTask = investmentTask;
    }

    public BigDecimal getTotalAmount() {
        return years.stream()
                .map(BudgetYear::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAmountForYear(int year) {
        return years.stream()
                .filter(budgetYear -> budgetYear.getYear() == year)
                .map(BudgetYear::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public void addYear(int year, BigDecimal amount) {
        BudgetYear budgetYear = new BudgetYear(this, year, amount);
        years.add(budgetYear);
    }

    public Long getId() {
        return id;
    }

    public InvestmentTask getInvestmentTask() {
        return investmentTask;
    }

    public List<BudgetYear> getYears() {
        return years;
    }
}
