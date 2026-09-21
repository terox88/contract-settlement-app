package pl.company.settlements.contractor.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContractorResponse {

    private Long id;
    private String name;
    private String taxId;
    private String street;
    private String buildingNumber;
    private String apartmentNumber;
    private String postalCode;
    private String city;
    private String country;
    private String email;
}