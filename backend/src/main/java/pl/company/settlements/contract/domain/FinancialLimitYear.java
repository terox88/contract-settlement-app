package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "financial_limit_year",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_financial_limit_year",
                columnNames = {"financial_limit_id", "year"}
        )
)
@Getter
@NoArgsConstructor

public class FinancialLimitYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_limit_id", nullable = false)
    private FinancialLimit financialLimit;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;


    FinancialLimitYear(
            FinancialLimit financialLimit,
            int year,
            BigDecimal amount
    ) {
        this.financialLimit = financialLimit;
        this.year = year;
        this.amount = amount;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

}