-- App-owned email verification codes for email/password registrations —
-- see EmailVerificationService and AuthController.register()/verifyEmail().
-- One row per email; resending overwrites the row in place rather than
-- accumulating history.
create table email_verification_codes (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    email varchar(255) not null unique,
    code_hash varchar(64) not null,
    expires_at timestamptz not null,
    attempts int not null default 0
);
