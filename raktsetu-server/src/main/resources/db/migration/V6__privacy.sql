-- Data protection (India's Digital Personal Data Protection Act, 2023).
-- Why a mobile number is held: CONSENT when the person turned alerts on themselves, EMPLOYER when an administrator
-- entered it for work alerts, UNRECORDED for numbers saved before this was tracked. notice_version is the privacy
-- notice the person was shown at the time.
alter table app_user add column contact_basis varchar(10);
alter table app_user add column contact_recorded_at bigint;
alter table app_user add column notice_version varchar(20);
update app_user set contact_basis = 'UNRECORDED' where phone is not null;

-- Former staff whose personal details were erased. The username stays, because the tamper-evident audit log refers to
-- it and cannot be rewritten.
alter table app_user add column anonymised_on date;
