-- Accounts. Centre and hospital staff are tied to one facility and may act only for it.
create table app_user (
    username      varchar(50)  primary key,
    display_name  varchar(100) not null,
    password_hash varchar(100) not null,
    role          varchar(20)  not null,
    facility_id   varchar(20)  references facility (id),
    enabled       boolean      not null
);
