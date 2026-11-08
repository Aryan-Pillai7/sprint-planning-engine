CREATE TABLE developers (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(190) NOT NULL,
    capacity_points INT          NOT NULL,
    CONSTRAINT uq_developers_email UNIQUE (email),
    CONSTRAINT ck_developers_capacity CHECK (capacity_points >= 0)
);

CREATE TABLE sprints (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    CONSTRAINT ck_sprints_dates CHECK (end_date >= start_date)
);

CREATE TABLE stories (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    servicenow_sys_id VARCHAR(32)   NULL,
    sprint_id         BIGINT        NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(4000) NULL,
    status            VARCHAR(20)   NOT NULL,
    story_points      INT           NULL,
    estimate_status   VARCHAR(20)   NOT NULL,
    CONSTRAINT uq_stories_servicenow_sys_id UNIQUE (servicenow_sys_id),
    CONSTRAINT fk_stories_sprint FOREIGN KEY (sprint_id) REFERENCES sprints (id),
    INDEX ix_stories_sprint (sprint_id),
    INDEX ix_stories_status (status),
    INDEX ix_stories_estimate_status (estimate_status)
);

CREATE TABLE tasks (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    story_id    BIGINT        NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(4000) NULL,
    status      VARCHAR(20)   NOT NULL,
    points      INT           NULL,
    CONSTRAINT fk_tasks_story FOREIGN KEY (story_id) REFERENCES stories (id),
    INDEX ix_tasks_story (story_id),
    INDEX ix_tasks_status (status)
);

CREATE TABLE estimates (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    story_id           BIGINT      NOT NULL,
    suggested_points   INT         NULL,
    override_points    INT         NULL,
    status             VARCHAR(20) NOT NULL,
    approved_by_id     BIGINT      NULL,
    created_at         DATETIME(6) NOT NULL,
    reviewed_at        DATETIME(6) NULL,
    CONSTRAINT fk_estimates_story FOREIGN KEY (story_id) REFERENCES stories (id),
    CONSTRAINT fk_estimates_approved_by FOREIGN KEY (approved_by_id) REFERENCES developers (id),
    INDEX ix_estimates_story_status (story_id, status),
    INDEX ix_estimates_approved_by (approved_by_id)
);

-- A work item (story or task) is assigned to one developer at most.
CREATE TABLE assignments (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    developer_id BIGINT      NOT NULL,
    story_id     BIGINT      NULL,
    task_id      BIGINT      NULL,
    strategy     VARCHAR(20) NOT NULL,
    assigned_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_assignments_story UNIQUE (story_id),
    CONSTRAINT uq_assignments_task UNIQUE (task_id),
    CONSTRAINT fk_assignments_developer FOREIGN KEY (developer_id) REFERENCES developers (id),
    CONSTRAINT fk_assignments_story FOREIGN KEY (story_id) REFERENCES stories (id),
    CONSTRAINT fk_assignments_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT ck_assignments_one_target CHECK ((story_id IS NULL) <> (task_id IS NULL)),
    INDEX ix_assignments_developer (developer_id)
);
