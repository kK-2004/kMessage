-- 渠道级用户（已解析的渠道 target id，如飞书 open_id）。用户属渠道，被所有绑定该渠道的应用共享。
create table app_users (
    id varchar(36) primary key,
    channel_instance_id varchar(36) not null references channel_instances(id),
    target_id varchar(255) not null,
    name varchar(255),
    phone varchar(64),
    email varchar(255),
    created_at timestamp with time zone not null,
    unique (channel_instance_id, target_id)
);

-- 分组树节点（应用+渠道作用域，自引用邻接表，parent_id 为 null 表示根节点）
create table user_groups (
    id varchar(36) primary key,
    caller_id varchar(36) not null references callers(id),
    channel_instance_id varchar(36) not null references channel_instances(id),
    parent_id varchar(36) references user_groups(id),
    name varchar(120) not null,
    created_at timestamp with time zone not null
);

-- 用户-分组多对多（一个渠道用户可属于某个应用的多个组）
create table user_group_members (
    group_id varchar(36) not null references user_groups(id),
    app_user_id varchar(36) not null references app_users(id),
    primary key (group_id, app_user_id)
);

