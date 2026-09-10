-- Run this once in the Supabase SQL Editor (Project -> SQL Editor -> New query).

create table if not exists inventory (
    product_id  varchar primary key,
    name        varchar not null,
    stock       integer not null check (stock >= 0)
);

create table if not exists orders (
    order_id    bigserial primary key,
    product_id  varchar not null references inventory(product_id),
    quantity    integer not null,
    status      varchar not null,
    reason      varchar,
    created_at  timestamp not null default now()
);

-- Seed data
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
