-- Facilities are created by administrators with a location, so routes can be worked out automatically.
alter table facility add column latitude double precision;
alter table facility add column longitude double precision;
alter table facility add column address varchar(300);

-- Where a route came from: OSRM road routing, a straight-line ESTIMATE, or a MANUAL correction by an administrator.
alter table route add column source varchar(12) default 'ESTIMATE' not null;

-- New and reset accounts get a one-time password and must choose their own at first sign-in.
alter table app_user add column must_change_password boolean default false not null;
alter table app_user add column created_on date;
