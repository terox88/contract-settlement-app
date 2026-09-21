package pl.company.settlements.contractor.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContractorUpdateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String taxId;

    private String street;

    private String buildingNumber;

    private String apartmentNumber;

    private String postalCode;

    private String city;

    private String country;

    @Email
    private String email;
}