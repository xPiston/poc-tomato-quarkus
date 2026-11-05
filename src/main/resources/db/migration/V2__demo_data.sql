-- Jeu de demonstration.
--
-- Les POC hexagonaux de la famille font ce travail dans une classe Java
-- (`DemoDataSeeder`) qui s'execute au demarrage, construit les reservations en
-- passant par le domaine, verifie d'abord que la base est vide, et qu'il faut
-- desactiver par variable d'environnement sur le second conteneur pour eviter
-- que les deux seedent en course.
--
-- Ici c'est une migration. Flyway garantit deja l'execution unique, meme si
-- dix instances demarrent ensemble -- il pose un verrou sur sa table d'etat.
-- C'est exactement ce que tomato appelle ne pas reimplementer ce que
-- l'outillage fait deja, et l'implementation de reference de tomato seede de
-- la meme facon (`V3__insert_products.sql`).
--
-- Le prix a payer : ces lignes n'ont pas traverse les regles du domaine. Un
-- jeu de demonstration incoherent passerait. C'est le role de
-- `DemoDataMigrationTest`, qui relit ces donnees par l'application et verifie
-- qu'elles tiennent debout.

-- Identifiants fixes : le fichier peut se relire, et les scenarios de demo
-- sont reperables dans la base.
insert into resources (id, name, capacity) values
    ('11111111-1111-4111-8111-111111111111', 'Salle Everest', 1),
    ('22222222-2222-4222-8222-222222222222', 'Salle Mont-Blanc', 1),
    ('33333333-3333-4333-8333-333333333333', 'Salle Kilimandjaro', 1),
    ('44444444-4444-4444-8444-444444444444', 'Espace Coworking (open space)', 4);

-- Mot de passe des trois comptes : "password". L'empreinte est la meme pour
-- les trois, ce qui se voit et ne genera personne : c'est un jeu de demo.
-- `DemoDataMigrationTest` verifie que cette empreinte correspond bien a
-- "password", pour qu'une faute de frappe ici ne se decouvre pas a la
-- premiere tentative de connexion.
insert into users (id, attendee_id, email, name, password) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
     'alice@booking.test', 'Alice Martin',
     '$2a$10$jXaO21LjwfL6uL/JYH8LVuGsP/jSLAoy.O3AzWHKEmOhUVR8WGTIu'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1',
     'bob@booking.test', 'Bob Durand',
     '$2a$10$jXaO21LjwfL6uL/JYH8LVuGsP/jSLAoy.O3AzWHKEmOhUVR8WGTIu'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'c1c1c1c1-c1c1-4c1c-8c1c-c1c1c1c1c1c1',
     'carol@booking.test', 'Carol Nguyen',
     '$2a$10$jXaO21LjwfL6uL/JYH8LVuGsP/jSLAoy.O3AzWHKEmOhUVR8WGTIu');

-- Un planning deja rempli : Alice le matin sur Everest, Bob l'apres-midi sur
-- Mont-Blanc, sur J+1, J+2, J+3 et J+5. Les dates sont relatives au
-- demarrage, donc la demo est toujours dans le futur quelle que soit la date
-- du jour -- et les reservations restent annulables, ce qui est l'interet.
insert into bookings (id, resource_id, attendee_id, starts_at, ends_at, status, waitlisted, requested_at)
select
    gen_random_uuid(),
    slot.resource_id,
    slot.attendee_id,
    date_trunc('day', now()) + (slot.day_offset || ' days')::interval + slot.starts_at,
    date_trunc('day', now()) + (slot.day_offset || ' days')::interval + slot.ends_at,
    'CONFIRMED',
    false,
    now() - interval '2 days'
from (
    select *
    from (values (1), (2), (3), (5)) as days (day_offset)
    cross join (values
        ('11111111-1111-4111-8111-111111111111'::uuid, 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1'::uuid,
         interval '9 hours', interval '10 hours 30 minutes'),
        ('22222222-2222-4222-8222-222222222222'::uuid, 'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1'::uuid,
         interval '14 hours', interval '15 hours')
    ) as rooms (resource_id, attendee_id, starts_at, ends_at)
) as slot;

-- Le scenario liste d'attente, le plus interessant a regarder tourner :
-- Everest est pris demain de 11 h a 12 h par Alice, et Bob attend sur le meme
-- creneau. Annuler la reservation d'Alice promeut celle de Bob et lui envoie
-- un mail, visible dans Mailpit.
--
-- A noter : la demande de Bob expire au bout de quinze minutes comme toute
-- demande non confirmee -- `Booking.isExpirable` ne fait pas d'exception pour
-- la liste d'attente. C'est discutable, et c'est le comportement des POC
-- hexagonaux, qu'on garde a l'identique pour que la comparaison reste
-- honnete. Pour rejouer le scenario : `docker compose down -v && docker
-- compose up`.
insert into bookings (id, resource_id, attendee_id, starts_at, ends_at, status, waitlisted, requested_at) values
    (gen_random_uuid(),
     '11111111-1111-4111-8111-111111111111', 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
     date_trunc('day', now()) + interval '1 day 11 hours',
     date_trunc('day', now()) + interval '1 day 12 hours',
     'CONFIRMED', false, now() - interval '2 days'),
    (gen_random_uuid(),
     '11111111-1111-4111-8111-111111111111', 'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1',
     date_trunc('day', now()) + interval '1 day 11 hours',
     date_trunc('day', now()) + interval '1 day 12 hours',
     'PENDING', true, now());
