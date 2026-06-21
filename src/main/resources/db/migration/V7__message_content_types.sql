alter table messages add column content_type varchar(32) not null default 'TEXT';
alter table messages add column content_json text;
