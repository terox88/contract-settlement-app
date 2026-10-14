package pl.company.settlements.contract.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractType {
    SERVICE ("Usługa"),
    DESIGN_AND_BUILD ("Zaprojektuj i wybuduj"),
    CONSTRUCTION_WORKS ("Roboty budowlane"),
    DELIVERY ("Dostawy"),
    OTHER ("Inne");

   private final String status;

}
