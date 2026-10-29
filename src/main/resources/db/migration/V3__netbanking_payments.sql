ALTER TABLE payments DROP CONSTRAINT ck_payments_method;
ALTER TABLE payments ADD CONSTRAINT ck_payments_method CHECK (method IN ('UPI', 'CARD', 'NETBANKING'));
