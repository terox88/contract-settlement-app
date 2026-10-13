package pl.company.settlements.budget.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.budget.domain.Budget;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByInvestmentTask_Id(Long investmentTaskId);

    boolean existsByInvestmentTask_Id(Long investmentTaskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Budget b where b.investmentTask.id = :investmentTaskId")
    Optional<Budget> findByInvestmentTaskIdForUpdate(
            @Param("investmentTaskId") Long investmentTaskId
    );
}