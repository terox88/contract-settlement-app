package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.contract.domain.FinancialLimit;

import java.util.List;
import java.util.Optional;

public interface FinancialLimitRepository
        extends JpaRepository<FinancialLimit, Long> {

    Optional<FinancialLimit> findByMainContract_Id(Long mainContractId);

    boolean existsByMainContract_Id(Long mainContractId);

    @Query("""
        select distinct fl
        from FinancialLimit fl
        left join fetch fl.years
        where fl.mainContract.investmentTask.id =
              :investmentTaskId
        """)
    List<FinancialLimit> findAllWithYearsByInvestmentTaskId(
            @Param("investmentTaskId")
            Long investmentTaskId
    );
}