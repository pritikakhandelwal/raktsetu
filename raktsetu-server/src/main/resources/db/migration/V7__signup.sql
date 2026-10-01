-- Requests to join: anyone may ask for a blood centre or hospital staff account from the sign-in page. The account is
-- created disabled and pending, and can be used only after an administrator approves it.
alter table app_user add column pending boolean default false not null;
