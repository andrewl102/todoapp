# --- !Ups

create table todo_item
(
  id        BIGSERIAL             NOT NULL
    CONSTRAINT todo_item_pkey
    PRIMARY KEY,
  title     VARCHAR(200)          NOT NULL,
  completed BOOLEAN DEFAULT FALSE NOT NULL,
  comments  VARCHAR(500) []       NOT NULL
);

# --- !Downs
drop table if exists todo_item;

