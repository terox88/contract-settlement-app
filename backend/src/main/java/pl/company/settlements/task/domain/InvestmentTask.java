package pl.company.settlements.task.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.company.settlements.budget.domain.Budget;
import pl.company.settlements.contract.domain.MainContract;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "investment_task")
public class InvestmentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_number", nullable = false, unique = true)
    private String taskNumber;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @OneToOne(mappedBy = "investmentTask", cascade = CascadeType.ALL, orphanRemoval = true)
    private Budget budget;

    @OneToMany(mappedBy = "investmentTask", cascade = CascadeType.ALL)
    private List<MainContract> mainContracts = new ArrayList<>();

    public InvestmentTask(String taskNumber, String name) {
        this.taskNumber = taskNumber;
        this.name = name;
    }
}
