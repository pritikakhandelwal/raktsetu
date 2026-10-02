-- OpenStreetMap entries in the directory have longer ids (for example OSMn12345678901), and people may now ask to join
-- a facility that is in neither the network nor the directory (requested_place: the name and town they typed).
alter table directory_facility alter column id set data type varchar(20);
alter table facility alter column directory_id set data type varchar(20);
alter table app_user alter column requested_directory_id set data type varchar(20);
alter table app_user add column requested_place varchar(200);
