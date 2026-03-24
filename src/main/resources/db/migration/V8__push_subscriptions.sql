-- Web Push subscriptions (per device/browser) for logged-in users

create table user_push_subscriptions (
  id bigint not null auto_increment,
  user_id bigint not null,
  endpoint varchar(1024) not null,
  p256dh varchar(255) not null,
  auth varchar(255) not null,
  user_agent varchar(255) null,
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  primary key (id),
  unique key uk_user_push_subscriptions_endpoint (endpoint),
  index idx_user_push_subscriptions_user_id (user_id)
);
