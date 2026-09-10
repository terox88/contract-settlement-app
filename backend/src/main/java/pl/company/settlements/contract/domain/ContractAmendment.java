package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "contract_amendment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contract_effective_date",
                columnNames = {"contract_id", "effective_date"}
        )
)
public class ContractAmendment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "amendment_number", nullable = false)
    private String amendmentNumber;

    @Column(name = "amendment_date", nullable = false)
    private LocalDate amendmentDate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "value_change", precision = 15, scale = 2)
    private BigDecimal valueChange;

    @Column(name = "new_start_date")
    private LocalDate newStartDate;

    @Column(name = "new_end_date")
    private LocalDate newEndDate;

    @Column(length = 2000)
    private String description;

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public BigDecimal getValueChange() { return valueChange; }
    public LocalDate getNewStartDate() { return newStartDate; }
    public LocalDate getNewEndDate() { return newEndDate; }
}
