package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import pl.company.settlements.contractor.domain.Contractor;

@Entity
@Table(name = "subcontract")
public class Subcontract extends Contract {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcontractor_id", nullable = false)
    private Contractor subcontractor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contract_id", nullable = false)
    private MainContract mainContract;
}
