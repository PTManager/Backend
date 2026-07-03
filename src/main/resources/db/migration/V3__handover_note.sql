CREATE TABLE handover_note (
    id           BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    workplace_id BIGINT      NOT NULL REFERENCES workplace(id),
    author_id    BIGINT      NOT NULL REFERENCES app_user(id),
    category     VARCHAR(16) NOT NULL
                 CHECK (category IN ('STOCK','DEVICE','CUSTOMER')),
    content      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_handover_workplace_created ON handover_note (workplace_id, created_at DESC);
