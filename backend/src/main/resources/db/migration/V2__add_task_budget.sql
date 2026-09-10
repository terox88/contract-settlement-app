CREATE TABLE budget (
    id BIGSERIAL PRIMARY KEY,
    investment_task_id BIGINT NOT NULL,
    CONSTRAINT fk_budget_investment_task
        FOREIGN KEY (investment_task_id) REFERENCES investment_task(id),
    CONSTRAINT uk_budget_investment_task UNIQUE (investment_task_id)
);

CREATE TABLE budget_year (
    id BIGSERIAL PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    year INTEGER NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    CONSTRAINT fk_budget_year_budget
        FOREIGN KEY (budget_id) REFERENCES budget(id),
    CONSTRAINT uk_budget_year UNIQUE (budget_id, year),
    CONSTRAINT chk_budget_year_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX idx_budget_year_budget ON budget_year(budget_id);
