package pl.company.settlements.contract.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.contract.domain.MainContract;

import java.util.List;
import java.util.Optional;

public interface MainContractRepository
        extends JpaRepository<MainContract, Long>,
        JpaSpecificationExecutor<MainContract> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from MainContract contract where contract.id = :id")
    Optional<MainContract> findByIdForUpdate(@Param("id") Long id);

    List<MainContract> findAllByInvestmentTask_Id(
            Long investmentTaskId
    );
}
