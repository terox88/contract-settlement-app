package pl.company.settlements.contract.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.company.settlements.contract.domain.ContractType;
import pl.company.settlements.contract.model.ContractTypeResponse;
import pl.company.settlements.contract.model.MainContractResponse;
import pl.company.settlements.contract.model.SubcontractResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractTypeControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dictionaryContainsEveryEnumValueWithItsLabel() {
        List<ContractTypeResponse> responses = new ContractTypeController().getAll();

        assertEquals(ContractType.values().length, responses.size());
        for (int index = 0; index < responses.size(); index++) {
            assertEquals(ContractType.values()[index].name(), responses.get(index).getValue());
            assertEquals(ContractType.values()[index].getStatus(), responses.get(index).getLabel());
        }
        JsonNode json = mapper.valueToTree(responses.stream()
                .filter(response -> response.getValue().equals("DELIVERY"))
                .findFirst().orElseThrow());
        assertEquals("DELIVERY", json.get("value").asText());
        assertEquals("Dostawy", json.get("label").asText());
    }

    @Test
    void mainContractKeepsEnumCodeAndAddsItsLabel() {
        MainContractResponse response = MainContractResponse.builder()
                .contractType(ContractType.DELIVERY)
                .build();

        JsonNode json = mapper.valueToTree(response);

        assertEquals("DELIVERY", json.get("contractType").asText());
        assertEquals("Dostawy", json.get("contractTypeLabel").asText());
    }

    @Test
    void subcontractKeepsEnumCodeAndAddsItsLabel() {
        SubcontractResponse response = SubcontractResponse.builder()
                .contractType(ContractType.OTHER)
                .build();

        JsonNode json = mapper.valueToTree(response);

        assertEquals("OTHER", json.get("contractType").asText());
        assertEquals("Inne", json.get("contractTypeLabel").asText());
    }

    @Test
    void missingContractTypeHasNoLabel() {
        assertNull(MainContractResponse.builder().build().getContractTypeLabel());
        assertNull(SubcontractResponse.builder().build().getContractTypeLabel());
    }
}
