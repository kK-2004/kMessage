alter table api_credentials add column app_key varchar(80);
alter table api_credentials add column secret_hash varchar(64);
update api_credentials set app_key = key_prefix, secret_hash = key_hash;
alter table api_credentials alter column app_key set not null;
alter table api_credentials alter column secret_hash set not null;
alter table api_credentials add constraint uk_api_credentials_app_key unique (app_key);
alter table api_credentials drop column key_hash;
alter table api_credentials drop column key_prefix;
