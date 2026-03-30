create table session_join_request (
    id bigint not null auto_increment,
    ladder_config_id bigint not null,
    requester_user_id bigint not null,
    invite_code_snapshot varchar(64) not null,
    status varchar(16) not null,
    requested_at datetime(6) not null,
    expires_at datetime(6) not null,
    resolved_at datetime(6),
    resolved_by_user_id bigint,
    updated_at datetime(6) not null,
    version bigint not null,
    primary key (id),
    constraint uk_session_join_request_ladder_requester unique (ladder_config_id, requester_user_id),
    constraint fk_session_join_request_ladder
        foreign key (ladder_config_id)
        references ladder_config (id)
);

create index idx_session_join_request_pending
    on session_join_request (ladder_config_id, status, requested_at);

create index idx_session_join_request_requester
    on session_join_request (requester_user_id, status);
