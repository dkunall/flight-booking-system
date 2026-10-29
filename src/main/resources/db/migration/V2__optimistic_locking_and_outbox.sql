-- Optimistic locking on the two aggregates that are updated from several code paths.
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Transactional outbox: booking events are written in the same transaction as the state change
-- and relayed afterwards, so a crash can't lose an event or emit one for a rolled-back change.
CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(30)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(40)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
