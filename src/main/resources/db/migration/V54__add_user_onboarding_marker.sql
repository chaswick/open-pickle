create table user_onboarding_marker (
    id bigint not null auto_increment,
    user_id bigint not null,
    marker_key varchar(64) not null,
    completed_at datetime(6) not null,
    primary key (id),
    constraint uk_user_onboarding_marker_user_key unique (user_id, marker_key),
    constraint fk_user_onboarding_marker_user
        foreign key (user_id)
        references users (id)
);

create index idx_user_onboarding_marker_key
    on user_onboarding_marker (marker_key, completed_at);
