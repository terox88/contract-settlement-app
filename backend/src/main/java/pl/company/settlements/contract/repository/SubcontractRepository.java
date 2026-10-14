package pl.company.settlements.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.contract.domain.Subcontract;

import java.util.List;
import java.util.Optional;

public interface SubcontractRepository
        extends JpaRepository<Subcontract, Long>,
        JpaSpecificationExecutor<Subcontract> {

    List<Subcontract> findAllByMainContract_Id(Long mainContractId);

    @Query("select subcontract.mainContract.id from Subcontract subcontract where subcontract.id = :id")
    Optional<Long> findMainContractIdById(@Param("id") Long id);
}
