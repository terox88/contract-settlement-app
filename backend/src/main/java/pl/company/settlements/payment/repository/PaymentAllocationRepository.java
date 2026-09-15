package pl.company.settlements.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.company.settlements.payment.domain.PaymentAllocation;

public interface PaymentAllocationRepository
        extends JpaRepository<PaymentAllocation, Long> {
}