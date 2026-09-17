-- Run this in the Supabase SQL Editor (Project -> SQL Editor -> New query).
-- This DROPS and recreates every table from scratch, including seed data,
-- so it's safe to re-run any time you want a clean slate.

drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id  varchar primary key,
    name        varchar not null,
    stock       integer not null check (stock >= 0)
);

create table orders (
    order_id    bigserial primary key,
    status      varchar not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      varchar,
    created_at  timestamp not null default now()
);

create table order_items (
    order_item_id bigserial primary key,
    order_id      bigint not null references orders(order_id) on delete cascade,
    product_id    varchar not null references inventory(product_id),
    quantity      integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigserial primary key,
    message          varchar not null,
    created_at       timestamp not null default now()
);

-- Seed data
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
