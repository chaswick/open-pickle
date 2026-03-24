
    create table band_positions (
        band_index integer,
        daily_momentum integer,
        position_in_band integer,
        id bigint not null auto_increment,
        season_id bigint not null,
        user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table competition_display_name_report (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        reporter_user_id bigint not null,
        target_user_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table competition_suspicious_match_flag (
        severity integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        match_id bigint not null,
        season_id bigint not null,
        details TEXT,
        summary varchar(255) not null,
        reason_code enum ('CLOSED_PLAYER_POD','MAXIMIZED_DELTA','RAPID_TURNAROUND','REPEATED_MAXIMIZED_DELTA_PATTERN') not null,
        primary key (id)
    ) engine=InnoDB;

    create table group_trophy (
        award_count integer not null,
        awarded_at datetime(6) not null,
        first_awarded_at datetime(6) not null,
        id bigint not null auto_increment,
        last_awarded_at datetime(6) not null,
        season_id bigint not null,
        trophy_id bigint not null,
        awarded_reason varchar(512),
        award_match_ids TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table interpretation_event (
        auto_submitted bit,
        created_at datetime(6) not null,
        current_user_id bigint,
        id bigint not null auto_increment,
        ladder_config_id bigint,
        match_id bigint,
        season_id bigint,
        event_uuid varchar(36) not null,
        interpreter_version varchar(255),
        interpretation_json MEDIUMTEXT,
        transcript MEDIUMTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_config (
        allow_guest_only_personal_matches bit not null,
        carry_over_previous_rating bit not null,
        max_transitions_per_day integer not null,
        pending_deletion bit not null,
        rolling_every_count integer not null,
        story_mode_default_enabled bit not null,
        tournament_mode bit not null,
        created_at datetime(6) not null,
        expires_at datetime(6),
        id bigint not null auto_increment,
        last_invite_change_at datetime(6),
        last_season_created_at datetime(6),
        owner_user_id bigint not null,
        pending_deletion_at datetime(6),
        pending_deletion_by_user_id bigint,
        target_season_id bigint,
        updated_at datetime(6) not null,
        version bigint not null,
        invite_code varchar(64),
        title varchar(120) not null,
        mode enum ('MANUAL','ROLLING') not null,
        rolling_every_unit enum ('MONTHS','WEEKS') not null,
        scoring_algorithm enum ('BALANCED_V1','MARGIN_CURVE_V1') not null,
        security_level enum ('HIGH','NONE','SELF_CONFIRM','STANDARD') not null,
        status enum ('ACTIVE','ARCHIVED') not null,
        type enum ('COMPETITION','SESSION','STANDARD') not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_match_link (
        id bigint not null auto_increment,
        match_id bigint not null,
        season_id bigint not null,
        headline varchar(160),
        primary key (id)
    ) engine=InnoDB;

    create table ladder_meetup_rsvp (
        id bigint not null auto_increment,
        slot_id bigint not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        status enum ('CANT','IN','MAYBE') not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_meetup_slot (
        location_code varchar(2),
        canceled_at datetime(6),
        created_at datetime(6) not null,
        created_by_user_id bigint not null,
        id bigint not null auto_increment,
        ladder_config_id bigint not null,
        starts_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_membership (
        id bigint not null auto_increment,
        joined_at datetime(6) not null,
        ladder_config_id bigint not null,
        left_at datetime(6),
        user_id bigint not null,
        role enum ('ADMIN','MEMBER') not null,
        state enum ('ACTIVE','BANNED','LEFT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_rating_change (
        rating_after integer not null,
        rating_before integer not null,
        rating_delta integer not null,
        id bigint not null auto_increment,
        match_id bigint not null,
        occurred_at datetime(6) not null,
        season_id bigint not null,
        user_id bigint not null,
        details TEXT,
        summary varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_season (
        end_date date not null,
        standings_recalc_in_flight integer not null,
        start_date date not null,
        story_mode_enabled bit not null,
        ended_at datetime(6),
        ended_by_user_id bigint,
        id bigint not null auto_increment,
        ladder_config_id bigint not null,
        standings_recalc_last_finished_at datetime(6),
        standings_recalc_last_started_at datetime(6),
        started_at datetime(6) not null,
        started_by_user_id bigint,
        version bigint not null,
        name varchar(255) not null,
        state enum ('ACTIVE','ENDED','SCHEDULED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table ladder_standing (
        points integer not null,
        rank_no integer not null,
        id bigint not null auto_increment,
        season_id bigint not null,
        user_id bigint,
        display_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table match_confirmation (
        casual_mode_auto_confirmed bit not null,
        confirmed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        match_id bigint not null,
        player_id bigint not null,
        updated_at datetime(6) not null,
        team varchar(255) not null,
        method enum ('AUTO','MANUAL') not null,
        primary key (id)
    ) engine=InnoDB;

    create table matches (
        a1_delta integer,
        a1_guest bit not null,
        a2_delta integer,
        a2_guest bit not null,
        b1_delta integer,
        b1_guest bit not null,
        b2_delta integer,
        b2_guest bit not null,
        confidence_score integer,
        confirmation_locked bit not null,
        exclude_from_standings bit not null,
        score_a integer not null,
        score_b integer not null,
        score_estimated bit not null,
        sportsmanship_rebate_applied bit not null,
        user_corrected bit not null,
        a1_id bigint,
        a2_id bigint,
        b1_id bigint,
        b2_id bigint,
        cosigned_by_id bigint,
        created_at datetime(6) not null,
        disputed_at datetime(6),
        disputed_by_id bigint,
        edited_at datetime(6),
        edited_by_id bigint,
        id bigint not null auto_increment,
        logged_by_id bigint,
        played_at datetime(6) not null,
        season_id bigint not null,
        source_session_config_id bigint,
        version bigint not null,
        dispute_note TEXT,
        transcript TEXT,
        verification_notes TEXT,
        state enum ('CONFIRMED','FLAGGED','NULLIFIED','PROVISIONAL') not null,
        primary key (id)
    ) engine=InnoDB;

    create table name_corrections (
        count integer not null,
        id bigint not null auto_increment,
        ladder_config_id bigint,
        last_confirmed_at datetime(6),
        reporter_user_id bigint,
        user_id bigint not null,
        phonetic_key varchar(32),
        token_normalized varchar(200) not null,
        primary key (id)
    ) engine=InnoDB;

    create table play_location (
        latitude float(53) not null,
        longitude float(53) not null,
        created_at datetime(6) not null,
        created_by_user_id bigint not null,
        id bigint not null auto_increment,
        primary key (id)
    ) engine=InnoDB;

    create table play_location_alias (
        usage_count integer not null,
        first_used_at datetime(6) not null,
        id bigint not null auto_increment,
        last_used_at datetime(6) not null,
        location_id bigint not null,
        user_id bigint not null,
        phonetic_key varchar(40),
        display_name varchar(80) not null,
        normalized_name varchar(80) not null,
        primary key (id)
    ) engine=InnoDB;

    create table play_location_check_in (
        checked_in_at datetime(6) not null,
        ended_at datetime(6),
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        location_id bigint not null,
        user_id bigint not null,
        display_name varchar(80) not null,
        primary key (id)
    ) engine=InnoDB;

    create table round_robin (
        current_round integer not null,
        created_at datetime(6) not null,
        created_by bigint,
        id bigint not null auto_increment,
        season_id bigint not null,
        session_config_id bigint,
        name varchar(255),
        format enum ('FIXED_TEAMS','ROTATING_PARTNERS') not null,
        primary key (id)
    ) engine=InnoDB;

    create table round_robin_entry (
        bye bit not null,
        round_number integer not null,
        a1_id bigint,
        a2_id bigint,
        b1_id bigint,
        b2_id bigint,
        id bigint not null auto_increment,
        match_id bigint,
        round_robin_id bigint not null,
        primary key (id)
    ) engine=InnoDB;

    create table score_corrections (
        corrected_value integer,
        count integer not null,
        interpreted_value integer,
        id bigint not null auto_increment,
        ladder_config_id bigint,
        last_confirmed_at datetime(6),
        score_field varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table trophy (
        badge_selectable_by_all bit,
        display_order integer,
        is_default_template bit,
        is_limited bit,
        is_repeatable bit,
        max_claims integer,
        regeneration_count integer,
        story_mode_tracker bit,
        art_id bigint,
        badge_art_id bigint,
        generated_at datetime(6) not null,
        id bigint not null auto_increment,
        season_id bigint,
        updated_at datetime(6) not null,
        ai_provider varchar(64),
        story_mode_key varchar(64),
        slug varchar(80) not null,
        title varchar(96) not null,
        generation_seed varchar(128),
        summary varchar(140) not null,
        unlock_condition varchar(512),
        unlock_expression varchar(512),
        prompt TEXT,
        evaluation_scope enum ('GROUP','USER') not null,
        rarity enum ('COMMON','EPIC','LEGENDARY','RARE','UNCOMMON') not null,
        status enum ('ARCHIVED','FAILED','GENERATED','PENDING_IMAGE') not null,
        primary key (id)
    ) engine=InnoDB;

    create table trophy_art (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        storage_key varchar(64) not null,
        image_url varchar(512),
        image_bytes LONGBLOB,
        primary key (id)
    ) engine=InnoDB;

    create table user_court_name (
        id bigint not null auto_increment,
        ladder_config_id bigint,
        user_id bigint not null,
        alias varchar(64) not null,
        primary key (id)
    ) engine=InnoDB;

    create table user_credit (
        amount float(53) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        match_id bigint,
        user_id bigint not null,
        reason varchar(128),
        primary key (id)
    ) engine=InnoDB;

    create table user_display_name_audit (
        changed_at datetime(6) not null,
        changed_by_user_id bigint not null,
        id bigint not null auto_increment,
        ladder_config_id bigint,
        user_id bigint not null,
        new_display_name varchar(24) not null,
        old_display_name varchar(24) not null,
        primary key (id)
    ) engine=InnoDB;

    create table user_push_subscriptions (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        -- Push endpoints are URLs from browser push providers and remain ASCII in practice.
        -- Using ASCII here keeps the unique constraint valid on MySQL 8/utf8mb4 clean installs.
        endpoint varchar(1024) character set ascii not null,
        auth varchar(255) not null,
        p256dh varchar(255) not null,
        user_agent varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table user_trophy (
        award_count integer not null,
        awarded_at datetime(6) not null,
        first_awarded_at datetime(6),
        id bigint not null auto_increment,
        last_awarded_at datetime(6),
        trophy_id bigint not null,
        user_id bigint not null,
        awarded_reason varchar(512),
        metadata varchar(512),
        primary key (id)
    ) engine=InnoDB;

    create table users (
        app_ui_enabled bit not null,
        competition_safe_display_name_active bit not null,
        consecutive_match_logs integer,
        failed_passphrase_attempts integer,
        is_admin bit,
        max_owned_ladders integer,
        meetups_email_daily_sent_count integer,
        meetups_email_daily_sent_day date,
        meetups_email_opt_in bit not null,
        meetups_email_pending bit not null,
        acknowledged_terms_at datetime(6),
        badge_slot_1_trophy_id bigint,
        badge_slot_2_trophy_id bigint,
        badge_slot_3_trophy_id bigint,
        id bigint not null auto_increment,
        last_display_name_change_at datetime(6),
        last_match_logged_at datetime(6),
        last_seen_at datetime(6),
        meetups_email_last_sent_at datetime(6),
        passphrase_timeout_until datetime(6),
        registered_at datetime(6),
        reset_password_token_expires_at datetime(6),
        public_code varchar(17),
        nick_name varchar(24) not null,
        reset_password_token varchar(30),
        competition_safe_display_name varchar(32),
        email varchar(45) not null,
        competition_safe_display_name_basis varchar(64),
        password varchar(64) not null,
        time_zone varchar(64),
        primary key (id)
    ) engine=InnoDB;

    create table user_style (
        id bigint not null auto_increment,
        style_owner bigint not null,
        style_voter bigint not null,
        voted_dt datetime(6),
        style_name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    alter table band_positions 
       add constraint uk_band_positions_season_user unique (season_id, user_id);

    create index idx_comp_name_report_target 
       on competition_display_name_report (target_user_id);

    create index idx_comp_name_report_reporter 
       on competition_display_name_report (reporter_user_id);

    alter table competition_display_name_report 
       add constraint uc_comp_name_report_reporter_target unique (reporter_user_id, target_user_id);

    create index idx_comp_suspicious_season_created 
       on competition_suspicious_match_flag (season_id, created_at);

    create index idx_comp_suspicious_match 
       on competition_suspicious_match_flag (match_id);

    alter table competition_suspicious_match_flag 
       add constraint uc_comp_suspicious_match_reason unique (match_id, reason_code);

    alter table group_trophy 
       add constraint uk_group_trophy_pair unique (season_id, trophy_id);

    create index ie_event_uuid_idx 
       on interpretation_event (event_uuid);

    create index ie_ladder_idx 
       on interpretation_event (ladder_config_id);

    alter table interpretation_event 
       add constraint UK_doe4qn1yaycotjxxmky2kg6qq unique (event_uuid);

    alter table ladder_config 
       add constraint UK_l6m4lp9hmh08vr180vo361ela unique (invite_code);

    alter table ladder_match_link 
       add constraint UKgu49e45d6yberhl0pj2q96xyp unique (match_id);

    alter table ladder_meetup_rsvp 
       add constraint UKh7nai14qxo7s3n1jjhcw42n unique (slot_id, user_id);

    alter table ladder_membership 
       add constraint UKbe7aouqlqpp0r9qevjnrn7j4o unique (ladder_config_id, user_id);

    alter table ladder_rating_change 
       add constraint UKaodueo8wet25gtw5bh90gf8et unique (match_id, user_id);

    create index ix_ladder_state 
       on ladder_season (ladder_config_id, state);

    alter table match_confirmation 
       add constraint UKqh4tgrymkmx3uiwe15gmjsvgv unique (match_id, player_id);

    create index nc_token_idx 
       on name_corrections (token_normalized);

    create index nc_ladder_idx 
       on name_corrections (ladder_config_id);

    create index nc_phonetic_idx 
       on name_corrections (phonetic_key);

    alter table name_corrections 
       add constraint uc_token_ladder_user unique (token_normalized, ladder_config_id, user_id);

    alter table play_location_alias 
       add constraint UKtmh8qjfx80q3vfbxus8sifbxc unique (location_id, user_id, normalized_name);

    alter table trophy 
       add constraint uk_trophy_slug unique (slug);

    alter table trophy_art 
       add constraint uk_trophy_art_storage_key unique (storage_key);

    alter table user_court_name 
       add constraint UK4xfk7vwwbrf6kbc5wxyxca5ne unique (user_id, alias, ladder_config_id);

    create index idx_user_display_name_audit_ladder_changed_at 
       on user_display_name_audit (ladder_config_id, changed_at);

    create index idx_user_display_name_audit_user_changed_at 
       on user_display_name_audit (user_id, changed_at);

    alter table user_push_subscriptions 
       add constraint uk_user_push_subscriptions_endpoint unique (endpoint);

    alter table user_trophy 
       add constraint uk_user_trophy_pair unique (user_id, trophy_id);

    alter table users 
       add constraint UK_eekqabuatqxq57npfmdhdlfmu unique (public_code);

    alter table users 
       add constraint UK_pl4047a5k5enw6up4sjs8lyut unique (nick_name);

    alter table users 
       add constraint UK_6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table band_positions 
       add constraint FKd3w7egwkjvgyygle0fdqbptos 
       foreign key (season_id) 
       references ladder_season (id);

    alter table band_positions 
       add constraint FKagwtr9slr01rmwlwhvja4qbn9 
       foreign key (user_id) 
       references users (id);

    alter table competition_suspicious_match_flag 
       add constraint FKqha0xmxswtok6oedkadkkrch1 
       foreign key (match_id) 
       references matches (id);

    alter table competition_suspicious_match_flag 
       add constraint FKo9711xd19whp2qpqj9qwefvqt 
       foreign key (season_id) 
       references ladder_season (id);

    alter table group_trophy 
       add constraint fk_group_trophy_season 
       foreign key (season_id) 
       references ladder_season (id);

    alter table group_trophy 
       add constraint fk_group_trophy_trophy 
       foreign key (trophy_id) 
       references trophy (id);

    alter table ladder_match_link 
       add constraint FKmx7pdjicr5nkwh0wfbatkp1w9 
       foreign key (match_id) 
       references matches (id);

    alter table ladder_match_link 
       add constraint FKdqnxuwrqpxdl6gjqinw5hbikb 
       foreign key (season_id) 
       references ladder_season (id);

    alter table ladder_meetup_rsvp 
       add constraint FKeusmyrx12mojqjp3312i4nfhw 
       foreign key (slot_id) 
       references ladder_meetup_slot (id);

    alter table ladder_meetup_slot 
       add constraint FKf51r3gldriayg2hfyiy8bdjal 
       foreign key (ladder_config_id) 
       references ladder_config (id);

    alter table ladder_membership 
       add constraint FKri0soo64ptdj51s7p1yydlble 
       foreign key (ladder_config_id) 
       references ladder_config (id);

    alter table ladder_rating_change 
       add constraint FK9y2f4d58to8nkim32lgmu2mg9 
       foreign key (match_id) 
       references matches (id);

    alter table ladder_rating_change 
       add constraint FKma1m02ftw8li5pf7s4ewthuk1 
       foreign key (season_id) 
       references ladder_season (id);

    alter table ladder_rating_change 
       add constraint FKb7tih58lb9hq7cmpdf3g3lee1 
       foreign key (user_id) 
       references users (id);

    alter table ladder_season 
       add constraint FKfpxro7x7rq08cshnmatqt5v7q 
       foreign key (ladder_config_id) 
       references ladder_config (id);

    alter table ladder_standing 
       add constraint FK85r02x2tsnmxdittasns4o8fm 
       foreign key (season_id) 
       references ladder_season (id);

    alter table ladder_standing 
       add constraint FKf9ugv5tgrew0txw0x7je62y0y 
       foreign key (user_id) 
       references users (id);

    alter table match_confirmation 
       add constraint FK9lv7bcpwsx0qjq1c5m0qvgf3g 
       foreign key (match_id) 
       references matches (id);

    alter table match_confirmation 
       add constraint FK3ali0i6u7tfg440x6ft10v6f9 
       foreign key (player_id) 
       references users (id);

    alter table matches 
       add constraint FK3wtes924j2cnwhygcyh2x9sws 
       foreign key (a1_id) 
       references users (id);

    alter table matches 
       add constraint FKsrpih4i2wrsw56xl3v00ab0co 
       foreign key (a2_id) 
       references users (id);

    alter table matches 
       add constraint FKaar0qwyh0glvuqn1lhq6lh9r9 
       foreign key (b1_id) 
       references users (id);

    alter table matches 
       add constraint FKs8b9dn717ysqcnq7hbtwgyube 
       foreign key (b2_id) 
       references users (id);

    alter table matches 
       add constraint FKgt6e8ymoi6po84nfgwugbq0xf 
       foreign key (cosigned_by_id) 
       references users (id);

    alter table matches 
       add constraint FKelt2usrvnqh385gl4ti0og7a8 
       foreign key (disputed_by_id) 
       references users (id);

    alter table matches 
       add constraint FKsqw1sskmmu8sd0uag94gdofyj 
       foreign key (edited_by_id) 
       references users (id);

    alter table matches 
       add constraint FKmud5nq60hw0kqqg3cnyh1o11x 
       foreign key (logged_by_id) 
       references users (id);

    alter table matches 
       add constraint FKqeu422agsfc359c0qt5cbcne2 
       foreign key (season_id) 
       references ladder_season (id);

    alter table matches 
       add constraint FKqd0ak637qj3wk8kkqk246rx4f 
       foreign key (source_session_config_id) 
       references ladder_config (id);

    alter table play_location 
       add constraint FKdwvks38qc9wntmq6v7qxhfufj 
       foreign key (created_by_user_id) 
       references users (id);

    alter table play_location_alias 
       add constraint FKbcxdcemafi5697dkkbqlyl21e 
       foreign key (location_id) 
       references play_location (id);

    alter table play_location_alias 
       add constraint FK44d8l2nu1yjleeva4sovvv5ij 
       foreign key (user_id) 
       references users (id);

    alter table play_location_check_in 
       add constraint FKp8lcoo93prxwcbw4a56yft844 
       foreign key (location_id) 
       references play_location (id);

    alter table play_location_check_in 
       add constraint FKcjo23mo0vie1kv7sbw91n1at8 
       foreign key (user_id) 
       references users (id);

    alter table round_robin 
       add constraint FK9ldrnqjd5a6v5ni475m097xoo 
       foreign key (created_by) 
       references users (id);

    alter table round_robin 
       add constraint FKtquna8bi9wkmqncj8ojf7kgu1 
       foreign key (season_id) 
       references ladder_season (id);

    alter table round_robin 
       add constraint FK9ylaxhqkmfq93k057c4jo0wqa 
       foreign key (session_config_id) 
       references ladder_config (id);

    alter table round_robin_entry 
       add constraint FKb2brxh0glhq0vvmcflsh2owmw 
       foreign key (a1_id) 
       references users (id);

    alter table round_robin_entry 
       add constraint FKapcowkvyd61wvu2tyvxmpyafg 
       foreign key (a2_id) 
       references users (id);

    alter table round_robin_entry 
       add constraint FKpp6ub6ybh7vasyj8hl9c3c892 
       foreign key (b1_id) 
       references users (id);

    alter table round_robin_entry 
       add constraint FKs10q2el1s8yv29nyc6nb55mxc 
       foreign key (b2_id) 
       references users (id);

    alter table round_robin_entry 
       add constraint FKb1q1wr1qrkepsroheleqwkmqy 
       foreign key (round_robin_id) 
       references round_robin (id);

    alter table trophy 
       add constraint fk_trophy_art 
       foreign key (art_id) 
       references trophy_art (id);

    alter table trophy 
       add constraint fk_trophy_badge_art 
       foreign key (badge_art_id) 
       references trophy_art (id);

    alter table trophy 
       add constraint fk_trophy_season 
       foreign key (season_id) 
       references ladder_season (id);

    alter table user_court_name 
       add constraint FKki2ed1qjc7ti2icybjkrcix76 
       foreign key (ladder_config_id) 
       references ladder_config (id);

    alter table user_court_name 
       add constraint FKqhqjo6p6l2m7oe2xbyard0tk7 
       foreign key (user_id) 
       references users (id);

    alter table user_credit 
       add constraint FK5njdwxavqx567y0jl8h43ag5a 
       foreign key (user_id) 
       references users (id);

    alter table user_trophy 
       add constraint fk_user_trophy_trophy 
       foreign key (trophy_id) 
       references trophy (id);

    alter table user_trophy 
       add constraint fk_user_trophy_user 
       foreign key (user_id) 
       references users (id);

    alter table users 
       add constraint fk_users_badge_slot_1_trophy 
       foreign key (badge_slot_1_trophy_id) 
       references trophy (id);

    alter table users 
       add constraint fk_users_badge_slot_2_trophy 
       foreign key (badge_slot_2_trophy_id) 
       references trophy (id);

    alter table users 
       add constraint fk_users_badge_slot_3_trophy 
       foreign key (badge_slot_3_trophy_id) 
       references trophy (id);

    alter table user_style 
       add constraint FKt8qclhk0tsk4w35kcmqe8jua2 
       foreign key (style_owner) 
       references users (id);

    alter table user_style 
       add constraint FKme68c2i084jqkpi9igb4egbrh 
       foreign key (style_voter) 
       references users (id);

    create index idx_matches_created_at_season
       on matches (created_at, season_id);

    create index idx_matches_logged_by_season
       on matches (logged_by_id, season_id);

    create index idx_matches_logged_by_created_at
       on matches (logged_by_id, created_at);

    create index idx_matches_a1_created_at
       on matches (a1_id, created_at);

    create index idx_matches_a2_created_at
       on matches (a2_id, created_at);

    create index idx_matches_b1_created_at
       on matches (b1_id, created_at);

    create index idx_matches_b2_created_at
       on matches (b2_id, created_at);

    create index idx_matches_season_played
       on matches (season_id, played_at);

    create index idx_matches_a1_played_at
       on matches (a1_id, played_at);

    create index idx_matches_a2_played_at
       on matches (a2_id, played_at);

    create index idx_matches_b1_played_at
       on matches (b1_id, played_at);

    create index idx_matches_b2_played_at
       on matches (b2_id, played_at);

    create index idx_matches_source_session_config_played_at
       on matches (source_session_config_id, played_at);

    create index idx_matches_state_disputed_at
       on matches (state, disputed_at);

    create index idx_match_confirmation_player_match
       on match_confirmation (player_id, match_id);

    create index idx_interpretation_event_user_created_at
       on interpretation_event (current_user_id, created_at);

    create index idx_lrc_season_user_occurred
       on ladder_rating_change (season_id, user_id, occurred_at desc);

    create index idx_ladder_config_type_status
       on ladder_config (type, status);

    create index idx_ladder_config_expires_at
       on ladder_config (expires_at);

    create index idx_trophy_story_mode_tracker
       on trophy (season_id, story_mode_tracker, story_mode_key);

    create index idx_trophy_badge_selectable_by_all
       on trophy (badge_selectable_by_all, display_order, id);
-- Baseline schema for clean environments.
-- Existing installations continue to use the historical V* migration chain.
