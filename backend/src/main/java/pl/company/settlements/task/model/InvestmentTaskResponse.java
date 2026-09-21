package pl.company.settlements.task.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvestmentTaskResponse {

    private Long id;
    private String taskNumber;
    private String name;
    private String description;
}