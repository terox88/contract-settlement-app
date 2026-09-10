package pl.company.settlements.contract.domain;

import jakarta.persistence.*;
import pl.company.settlements.contractor.domain.Contractor;
import pl.company.settlements.task.domain.InvestmentTask;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "main_contract")
public class MainContract extends Contract {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "main_contractor_id", nullable = false)
    private Contractor mainContractor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_task_id", nullable = false)
    private InvestmentTask investmentTask;

    @OneToMany(mappedBy = "mainContract", cascade = CascadeType.ALL)
    private List<Subcontract> subcontracts = new ArrayList<>();
}
