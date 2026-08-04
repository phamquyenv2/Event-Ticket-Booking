ALTER TABLE voucher_redemptions
    ADD CONSTRAINT unique_voucher_user UNIQUE (voucher_id, user_id);
