-- Keep issued member codes and their unique index; allow longer codes for new accounts.
ALTER TABLE users MODIFY COLUMN public_code VARCHAR(24) NULL;
