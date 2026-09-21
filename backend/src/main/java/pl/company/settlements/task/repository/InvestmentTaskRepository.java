package pl.company.settlements.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pl.company.settlements.task.domain.InvestmentTask;

import java.util.Optional;

public interface InvestmentTaskRepository
        extends JpaRepository<InvestmentTask, Long>,
        JpaSpecificationExecutor<InvestmentTask> {

    Optional<InvestmentTask> findByTaskNumber(String taskNumber);

    boolean existsByTaskNumber(String taskNumber);
}