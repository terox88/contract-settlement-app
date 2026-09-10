package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "contract")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(name = "contract_number", nullable = false)
    protected String contractNumber;

    @Column(name = "contract_date", nullable = false)
    protected LocalDate contractDate;

    @Column(name = "initial_value", nullable = false, precision = 15, scale = 2)
    protected BigDecimal initialValue;

    @Column(name = "initial_start_date")
    protected LocalDate initialStartDate;

    @Column(name = "initial_end_date")
    protected LocalDate initialEndDate;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("effectiveDate ASC")
    protected List<ContractAmendment> amendments = new ArrayList<>();

    public BigDecimal getCurrentValue() {
        return amendments.stream()
                .map(ContractAmendment::getValueChange)
                .filter(Objects::nonNull)
                .reduce(initialValue, BigDecimal::add);
    }

    public BigDecimal getValueAt(LocalDate date) {
        return amendments.stream()
                .filter(a -> !a.getEffectiveDate().isAfter(date))
                .map(ContractAmendment::getValueChange)
                .filter(Objects::nonNull)
                .reduce(initialValue, BigDecimal::add);
    }

    public LocalDate getCurrentStartDate() {
        return amendments.stream()
                .filter(a -> a.getNewStartDate() != null)
                .max(Comparator.comparing(ContractAmendment::getEffectiveDate))
                .map(ContractAmendment::getNewStartDate)
                .orElse(initialStartDate);
    }

    public LocalDate getCurrentEndDate() {
        return amendments.stream()
                .filter(a -> a.getNewEndDate() != null)
                .max(Comparator.comparing(ContractAmendment::getEffectiveDate))
                .map(ContractAmendment::getNewEndDate)
                .orElse(initialEndDate);
    }
}
