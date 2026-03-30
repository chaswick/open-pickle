alter table ladder_config
    add column nearby_share_location_id bigint null,
    add column nearby_share_location_name varchar(80) null;
