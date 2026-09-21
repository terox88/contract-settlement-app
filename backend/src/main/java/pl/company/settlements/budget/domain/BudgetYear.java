package pl.company.settlements.budget.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "budget_year",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budget_year",
                columnNames = {"budget_id", "year"}
        )
)
public class BudgetYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected BudgetYear() {
    }

    BudgetYear(Budget budget, int year, BigDecimal amount) {
        this.budget = budget;
        this.year = year;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
