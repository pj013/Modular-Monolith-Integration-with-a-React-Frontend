-- Run this in the Supabase SQL Editor (Project -> SQL Editor -> New query).
-- This DROPS and recreates every table from scratch, including seed data,
-- so it's safe to re-run any time you want a clean slate.

drop table if exists notifications;
drop table if exists supplier_orders;
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

create table supplier_orders (
    id              bigserial primary key,
    product_id      varchar not null references inventory(product_id),
    buyer_ref       varchar(40) not null unique,
    request_id      varchar(80) not null unique,
    po_number       varchar unique,
    cases           integer not null default 0 check (cases >= 0),
    units           integer not null check (units > 0),
    received_units  integer not null default 0 check (received_units >= 0),
    status          varchar(32) not null check (status in
                        ('PENDING', 'ACCEPTED', 'PICKING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'UNKNOWN', 'FAILED')),
    attempt_count   integer not null default 0,
    next_attempt_at timestamp,
    last_error      varchar(500),
    created_at      timestamp not null default now(),
    updated_at      timestamp not null default now()
);

create index supplier_orders_pending_idx on supplier_orders(status, next_attempt_at);

-- Seed data
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
on conflict (product_id) do nothing;
