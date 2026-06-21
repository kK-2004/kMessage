create table channel_contacts (
    id varchar(36) primary key,
    channel_instance_id varchar(36) not null references channel_instances(id),
    target_id varchar(255) not null,
    label varchar(255) not null,
    contact_type varchar(32) not null,
    first_seen_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    unique (channel_instance_id, target_id)
);
