-- The first directory load (V9) kept some HTML character codes from the source data, for example "Hospital&#39;s".
-- The directory file is now cleaned when it is built; this fixes rows already loaded, and facilities created from them.
update directory_facility set
    name = replace(replace(name, '&#39;', ''''), '&#34;', '"'),
    address = replace(replace(address, '&#39;', ''''), '&#34;', '"'),
    city = replace(replace(city, '&#39;', ''''), '&#34;', '"'),
    search_text = replace(replace(search_text, '&#39;', ''''), '&#34;', '"');
update facility set
    name = replace(replace(name, '&#39;', ''''), '&#34;', '"'),
    address = replace(replace(address, '&#39;', ''''), '&#34;', '"')
where directory_id is not null;
