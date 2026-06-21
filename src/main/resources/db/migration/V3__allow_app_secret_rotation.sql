alter table api_credentials drop constraint uk_api_credentials_app_key;
create index idx_api_credentials_app_key_active on api_credentials(app_key, active);
