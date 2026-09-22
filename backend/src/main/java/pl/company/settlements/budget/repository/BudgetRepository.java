package pl.company.settlements.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.budget.domain.Budget;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByInvestmentTask_Id(Long investmentTaskId);

    boolean existsByInvestmentTask_Id(Long investmentTaskId);
}