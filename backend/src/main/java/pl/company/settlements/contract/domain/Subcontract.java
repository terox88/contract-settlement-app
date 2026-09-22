package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.contractor.domain.Contractor;
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "subcontract")
public class Subcontract extends Contract {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontractor_id", nullable = false)
    private Contractor subcontractor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contract_id", nullable = false)
    private MainContract mainContract;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "percentage",
                    column = @Column(
                            name = "performance_security_percentage",
                            precision = 5,
                            scale = 2
                    )
            ),
            @AttributeOverride(
                    name = "basis",
                    column = @Column(name = "performance_security_basis")
            )
    })
    private DeductionRule performanceSecurityDeduction;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "percentage",
                    column = @Column(
                            name = "other_deduction_percentage",
                            precision = 5,
                            scale = 2
                    )
            ),
            @AttributeOverride(
                    name = "basis",
                    column = @Column(name = "other_deduction_basis")
            )
    })
    private DeductionRule otherDeduction;

    public Subcontract(
            MainContract mainContract,
            Contractor subcontractor
    ) {
        this.mainContract = mainContract;
        this.subcontractor = subcontractor;
    }
}


