package pl.company.settlements.invoice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(name = "invoice_number", nullable = false)
    protected String invoiceNumber;

    @Column(name = "issue_date", nullable = false)
    protected LocalDate issueDate;

    @Column(name = "sale_date")
    protected LocalDate saleDate;

    @Column(name = "due_date")
    protected LocalDate dueDate;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    protected BigDecimal netAmount;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    protected BigDecimal vatAmount;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    protected BigDecimal grossAmount;

    @Column(nullable = false, length = 3)
    protected String currency = "PLN";

}
