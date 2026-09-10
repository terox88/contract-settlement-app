package pl.company.settlements.contractor.domain;

import jakarta.persistence.*;

@Entity
@Table(
        name = "contractor",
        uniqueConstraints = @UniqueConstraint(name = "uk_contractor_tax_id", columnNames = "tax_id")
)
public class Contractor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_id", nullable = false)
    private String taxId;

    private String street;

    @Column(name = "building_number")
    private String buildingNumber;

    @Column(name = "apartment_number")
    private String apartmentNumber;

    @Column(name = "postal_code")
    private String postalCode;

    private String city;

    private String country = "Polska";

    private String email;
}
