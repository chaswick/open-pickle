alter table ladder_config
    add column session_play_mode varchar(24) not null default 'OPEN_PLAY';

alter table round_robin
    add column ended_at datetime(6) null,
    add column session_flow bit(1) not null default 0;

alter table round_robin_entry
    add column a1_slot_id bigint null,
    add column a2_slot_id bigint null,
    add column b1_slot_id bigint null,
    add column b2_slot_id bigint null,
    add column a1_guest_name varchar(80) null,
    add column a2_guest_name varchar(80) null,
    add column b1_guest_name varchar(80) null,
    add column b2_guest_name varchar(80) null;

update round_robin_entry
set a1_slot_id = a1_id, a2_slot_id = a2_id,
    b1_slot_id = b1_id, b2_slot_id = b2_id;

alter table matches
    add column a1_guest_name varchar(80) null,
    add column a2_guest_name varchar(80) null,
    add column b1_guest_name varchar(80) null,
    add column b2_guest_name varchar(80) null;

update ladder_config l
set l.session_play_mode = 'MATCHUPS'
where l.type = 'SESSION'
  and exists (select 1 from round_robin rr where rr.session_config_id = l.id);
