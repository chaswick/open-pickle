-- Global Play Plans email digest preferences/state (per user)
alter table users
  add column meetups_email_opt_in tinyint(1) not null default 0,
  add column meetups_email_last_sent_at datetime(6) null,
  add column meetups_email_pending tinyint(1) not null default 0,
  add column meetups_email_daily_sent_count int null,
  add column meetups_email_daily_sent_day date null;

create index idx_users_meetups_email_pending on users(meetups_email_pending);