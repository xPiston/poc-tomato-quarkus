-- Schema initial.
--
-- Flyway possede ce fichier et Hibernate ne genere rien
-- (quarkus.hibernate-orm.database.generation=none) : le schema est ecrit a la
-- main, lu en revue, et joue a l'identique en dev, en test et en production.

create table resources (
    id uuid not null primary key,
    name varchar(255) not null,
    capacity integer not null check (capacity >= 1)
);

create table users (
    id uuid not null primary key,
    attendee_id uuid not null unique,
    email varchar(180) not null unique,
    name varchar(255) not null,
    password varchar(255) not null
);

create table api_tokens (
    token varchar(64) not null primary key,
    user_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null
);

-- `status` stocke le nom Java de l'enum (PENDING, CONFIRMED...) et non le
-- libelle francais affiche par le frontend : la base n'a pas a suivre les
-- choix de vocabulaire de l'interface. La traduction se fait dans BookingDto.
create table bookings (
    id uuid not null primary key,
    resource_id uuid not null,
    attendee_id uuid not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    status varchar(40) not null,
    waitlisted boolean not null,
    requested_at timestamptz not null,
    constraint bookings_slot_ordered check (ends_at > starts_at)
);

-- Les trois lectures chaudes : le planning d'une ressource sur une fenetre,
-- le balayage des demandes a expirer, et "mes reservations".
create index idx_bookings_resource_slot on bookings (resource_id, starts_at, ends_at);
create index idx_bookings_status_requested on bookings (status, requested_at);
create index idx_bookings_attendee on bookings (attendee_id);
