ALTER TABLE ticket_categories
    ADD COLUMN max_quantity_per_booking INT NOT NULL DEFAULT 4,
    ADD CONSTRAINT check_max_quantity_per_booking_positive
        CHECK (max_quantity_per_booking > 0);
