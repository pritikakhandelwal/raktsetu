-- A directory of Indian blood centres and hospitals from open government data (see tools/directory), so people can
-- find their own facility when they ask to join. It is reference data only: a facility becomes part of the network
-- when an administrator approves someone from it (or adds it on the Facilities page). V9 loads the rows.
create table directory_facility (
    id          varchar(12)  primary key,
    kind        varchar(12)  not null,
    name        varchar(200) not null,
    category    varchar(40)  not null,
    state       varchar(60)  not null,
    district    varchar(60)  not null,
    city        varchar(60)  not null,
    address     varchar(300) not null,
    pincode     varchar(10)  not null,
    latitude    double precision not null,
    longitude   double precision not null,
    search_text varchar(600) not null
);
create index directory_facility_kind on directory_facility (kind);

-- Which directory entry a network facility was created from, so a second person from the same place joins it.
alter table facility add column directory_id varchar(12);

-- A request to join a facility that is in the directory but not yet in the network.
alter table app_user add column requested_directory_id varchar(12);
