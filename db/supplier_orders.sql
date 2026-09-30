-- Additive migration for an existing Lab 2 database. Run once; unlike schema.sql,
-- this does not drop the inventory, order, or notification tables.
create table if not exists supplier_orders (
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

create index if not exists supplier_orders_pending_idx on supplier_orders(status, next_attempt_at);

alter table supplier_orders drop constraint if exists supplier_orders_status_check;
alter table supplier_orders add constraint supplier_orders_status_check check (status in
    ('PENDING', 'ACCEPTED', 'PICKING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'UNKNOWN', 'FAILED'));