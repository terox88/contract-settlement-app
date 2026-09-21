package pl.company.settlements.task.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvestmentTaskUpdateRequest {

    @NotBlank
    private String taskNumber;

    @NotBlank
    private String name;

    private String description;
}