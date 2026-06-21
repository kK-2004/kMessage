-- app_users used to be application-scoped in early local databases.
-- It is now channel-scoped and shared by all applications bound to a channel.
alter table app_users drop constraint if exists constraint_4aa;
alter table app_users drop constraint if exists constraint_4aa1b;
alter table app_users drop constraint if exists app_users_caller_id_fkey;
alter table app_users drop constraint if exists app_users_caller_id_channel_instance_id_target_id_key;
alter table app_users drop column if exists caller_id;
create unique index if not exists uq_app_users_channel_target on app_users(channel_instance_id, target_id);
