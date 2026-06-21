create table callers (
    id varchar(36) primary key,
    name varchar(120) not null unique,
    admin boolean not null default false,
    active boolean not null default true,
    created_at timestamp with time zone not null
);

create table api_credentials (
    id varchar(36) primary key,
    caller_id varchar(36) not null references callers(id),
    key_hash varchar(64) not null unique,
    key_prefix varchar(16) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone
);

create table channel_instances (
    id varchar(36) primary key,
    name varchar(120) not null unique,
    channel_type varchar(32) not null,
    enabled boolean not null default false,
    credential_ref varchar(255) not null,
    config_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table caller_channel_grants (
    caller_id varchar(36) not null references callers(id),
    channel_instance_id varchar(36) not null references channel_instances(id),
    primary key (caller_id, channel_instance_id)
);

create table messages (
    id varchar(36) primary key,
    caller_id varchar(36) not null references callers(id),
    channel_instance_id varchar(36) not null references channel_instances(id),
    target_value varchar(512) not null,
    content_text text not null,
    extension_json text not null,
    idempotency_key varchar(160) not null,
    request_hash varchar(64) not null,
    trace_id varchar(128),
    status varchar(32) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (caller_id, idempotency_key)
);

create table delivery_tasks (
    id varchar(36) primary key,
    message_id varchar(36) not null unique references messages(id),
    attempt_count integer not null default 0,
    next_attempt_at timestamp with time zone not null,
    lease_until timestamp with time zone,
    worker_id varchar(120),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create index idx_delivery_tasks_claim on delivery_tasks(next_attempt_at, lease_until);

create table delivery_attempts (
    id varchar(36) primary key,
    message_id varchar(36) not null references messages(id),
    attempt_number integer not null,
    result_type varchar(32) not null,
    provider_reference varchar(255),
    error_code varchar(120),
    diagnostic varchar(512),
    started_at timestamp with time zone not null,
    finished_at timestamp with time zone not null
);
create index idx_delivery_attempts_message on delivery_attempts(message_id, attempt_number);
