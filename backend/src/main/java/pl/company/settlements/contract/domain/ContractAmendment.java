package pl.company.settlements.contract.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "contract_amendment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contract_effective_date",
                columnNames = {
                        "contract_id",
                        "effective_date"
                }
        )
)
public class ContractAmendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "contract_id",
            nullable = false
    )
    private Contract contract;

    @Column(
            name = "amendment_number",
            nullable = false
    )
    private String amendmentNumber;

    @Column(
            name = "amendment_date",
            nullable = false
    )
    private LocalDate amendmentDate;

    @Column(
            name = "effective_date",
            nullable = false
    )
    private LocalDate effectiveDate;

    @Column(
            name = "value_change",
            precision = 15,
            scale = 2
    )
    private BigDecimal valueChange;


    @Column(name = "new_end_date")
    private LocalDate newEndDate;

    @Column(length = 2000)
    private String description;

    public ContractAmendment(Contract contract) {
        this.contract = contract;
    }
}