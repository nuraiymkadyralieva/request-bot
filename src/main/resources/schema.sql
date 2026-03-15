create table if not exists users (
    id bigserial primary key,
    telegram_id bigint not null unique,
    name varchar(255) not null,
    department varchar(255) not null,
    position varchar(255) not null
);

create table if not exists requests (
    id bigserial primary key,
    user_id bigint not null references users (id),
    type varchar(50) not null,
    description text not null,
    priority varchar(50) not null,
    status varchar(50) not null,
    created_at timestamp not null
);
