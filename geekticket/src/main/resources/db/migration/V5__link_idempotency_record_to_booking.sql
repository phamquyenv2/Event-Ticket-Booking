ALTER TABLE idempotency_records
    DROP COLUMN response_body,
    ADD COLUMN booking_id BIGINT REFERENCES bookings(id) ON DELETE CASCADE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT check_idempotency_key_uuid_like CHECK (
        idempotency_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    );
