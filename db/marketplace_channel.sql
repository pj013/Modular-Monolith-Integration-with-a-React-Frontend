-- Additive migration for the Tiangge channel; preserves existing shop data.
alter table orders add column if not exists external_reference varchar unique;
alter table orders drop constraint if exists orders_status_check;
alter table orders add constraint orders_status_check
    check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED', 'BACKORDERED'));

create table if not exists channel_state (
    state_id integer primary key check (state_id = 1),
    feed_cursor bigint not null default 0
);

insert into channel_state (state_id, feed_cursor)
values (1, 0)
on conflict (state_id) do nothing;

create table if not exists channel_events (
    event_id varchar(120) primary key,
    sequence_number bigint not null,
    event_type varchar(40) not null,
    marketplace_order_id varchar(80),
    local_order_id bigint,
    processed_at timestamp not null default now()
);

create table if not exists channel_stock_outbox (
    seller_sku varchar(40) primary key,
    available integer not null check (available >= 0),
    ready boolean not null default true,
    updated_at timestamp not null default now()
);

alter table channel_stock_outbox add column if not exists ready boolean not null default true;

create table if not exists channel_backorders (
    marketplace_order_id varchar(80) primary key,
    external_reference varchar not null unique,
    local_order_id bigint not null references orders(order_id),
    status varchar(24) not null check (status in ('WAITING', 'RESOLUTION_PENDING', 'RESOLVED', 'CANCELLED')),
    resolution_status varchar(16),
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create table if not exists channel_backorder_items (
    marketplace_order_id varchar(80) not null references channel_backorders(marketplace_order_id)
        on delete cascade,
    product_id varchar not null references inventory(product_id),
    quantity integer not null check (quantity > 0),
    primary key (marketplace_order_id, product_id)
);

create index if not exists channel_backorders_status_idx on channel_backorders(status);

create table if not exists channel_order_links (
    marketplace_order_id varchar(80) primary key,
    external_reference varchar not null unique,
    local_order_id bigint not null references orders(order_id),
    initial_decision varchar(16) not null check (initial_decision in ('ACCEPTED', 'REJECTED', 'BACKORDERED')),
    created_at timestamp not null default now()
);
