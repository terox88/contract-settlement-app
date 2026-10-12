ALTER TABLE investment_task
    ADD COLUMN status VARCHAR(30)
        NOT NULL
        DEFAULT 'IN_PROGRESS';

ALTER TABLE investment_task
    ADD CONSTRAINT chk_investment_task_status
        CHECK (
            status IN (
                       'IN_PROGRESS',
                       'COMPLETED',
                       'ON_HOLD',
                       'CANCELLED'
                )
            );