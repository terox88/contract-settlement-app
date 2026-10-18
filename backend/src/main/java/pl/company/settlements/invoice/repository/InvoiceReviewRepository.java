package pl.company.settlements.invoice.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.company.settlements.invoice.domain.MainContractorInvoice;
import pl.company.settlements.invoice.domain.MainInvoiceSettlement;
import pl.company.settlements.invoice.domain.SubcontractorInvoice;
import pl.company.settlements.payment.domain.PaymentAllocation;
import java.util.List;

public interface InvoiceReviewRepository extends Repository<MainContractorInvoice, Long> {
    @Query("""
        select i from SubcontractorInvoice i join fetch i.subcontract s join fetch s.subcontractor
        left join fetch i.statement where s.mainContract.id = :root
        """)
    List<SubcontractorInvoice> findSubInvoices(@Param("root") Long root);
    @Query("""
        select a from MainInvoiceSettlement a join fetch a.subcontractorInvoice i
        left join fetch a.mainContractorInvoice left join fetch a.parentSubcontractorInvoice
        where i.subcontract.mainContract.id = :root
        """)
    List<MainInvoiceSettlement> findAssignments(@Param("root") Long root);
    @Query("""
        select a from PaymentAllocation a join fetch a.payment
        left join fetch a.mainContractorInvoice mi left join fetch a.subcontractorInvoice si
        where mi.id in (select i.id from MainContractorInvoice i where i.mainContract.id = :root)
           or si.id in (select i.id from SubcontractorInvoice i where i.subcontract.mainContract.id = :root)
        """)
    List<PaymentAllocation> findAllocations(@Param("root") Long root);
}
