# POC — Tomato Architecture avec Quarkus

Le même projet que [la version hexagonale Quarkus](../poc-hexa-quarkus) — mêmes règles métier, même
API, même frontend React — mais organisé selon la [tomato
architecture](https://tomato-architecture.github.io/) de Siva Prasad Reddy au lieu de ports et
adapters.

L'intérêt de l'exercice n'est pas de montrer qu'on sait faire tourner un Quarkus. C'est que le
métier est **identique au mot près** : machine à états, disponibilité, liste d'attente, pénalité
d'annulation, expiration des demandes. Deux POC, les mêmes règles, les mêmes tests de domaine, le
même frontend — et deux façons opposées de ranger le code autour. Ce qui diffère est donc
imputable à l'architecture seule.

## Ce que dit tomato, et ce que ça change ici

Tomato part d'un constat : les abstractions que l'hexagonal impose (un port par dépendance, une
interface par service, un mapper entre chaque couche) sont payées tout de suite et rentabilisées
rarement. Il propose de les retirer et de les remplacer par des tests qui vérifient ce que les
types ne garantissent plus.

Les sept règles de la page [Implementation
Guidelines](https://tomato-architecture.github.io/#implementation-guidelines), appliquées :

| Règle de tomato | Dans la version hexagonale | Ici |
|---|---|---|
| **1. Package by feature** | `domain/`, `application/`, `infrastructure/`, `ui/` — par couche technique | `auth/`, `catalog/`, `bookings/` — par fonctionnalité |
| **2. Application Core for business logic** | Respecté (handlers de use case) | Respecté (`BookingService`) |
| **3. Separation of concerns** | Les ressources HTTP lisent les repositories directement | Les contrôleurs ne parlent qu'au service |
| **4. Domain logic in domain objects** | Respecté | Respecté — et la règle de capacité descend dans `BookableResource` |
| **5. No unnecessary interfaces** | 9 interfaces : 5 ports entrants, 4 ports sortants | 1, et c'est un `@ConfigMapping` que le framework implémente |
| **6. Embrace framework's power** | `Clock`, `Notifier` et les repositories sont réécrits à la main | `java.time.Clock`, `Mailer`, Panache, `@Scheduled`, Flyway |
| **7. Test whole features** | 4 niveaux de tests | Les mêmes 4 niveaux, plus les règles d'architecture |

### Ce que ça donne, en chiffres

|  | hexagonal | tomato |
|---|---|---|
| Fichiers Java (`src/main`) | 86 | **49** |
| Lignes (`src/main`) | 3 307 | **2 302** |
| Interfaces déclarées | 9 | **1** |
| Méthodes de test | 84 | **97** |

Moins de code pour les mêmes règles, et plus de tests — dont neuf qui vérifient l'architecture
elle-même, ce que la version hexagonale n'a pas besoin de faire puisque le compilateur s'en charge.
**C'est exactement le marché que propose tomato**, et il faut le voir comme un échange et pas comme
un gain sec : on troque une garantie statique contre une garantie testée.

## Les quatre décisions qui portent la différence

### 1. L'objet métier *est* l'entité JPA

La version hexagonale a un `Booking` de domaine, un `BookingRecord` JPA et un `BookingMapper` entre
les deux — trois fichiers qui décrivent les mêmes sept champs, à tenir synchronisés à la main.
Tomato appelle ça du *endless data copying* et le compte parmi les coûts qu'il refuse.

Ici [`Booking`](src/main/java/fr/dllv/booking/bookings/Booking.java) porte quatre annotations JPA et
toutes ses règles. Le `TimeSlot` reste un Value Object, mais devient `@Embeddable` : la règle de
chevauchement vit toujours avec la donnée, sans copie.

Ce qu'on perd : la persistance n'est plus un détail qu'on se réserve le droit de changer. C'est
assumé — on ne change pas de base de données un mardi.

### 2. Un service par fonctionnalité, pas un handler par cas d'usage

Les cinq cas d'usage deviennent cinq méthodes de
[`BookingService`](src/main/java/fr/dllv/booking/bookings/BookingService.java), au lieu de cinq
`...Handler` derrière cinq ports entrants avec leurs objets `Command` et `Result` — quinze fichiers
en moins.

La différence se lit dans l'annulation. Côté hexagonal, annuler doit déclencher la promotion de la
liste d'attente, donc le handler compose l'autre use case **par son port**, et pour franchir cette
frontière il resérialise les dates du créneau en chaînes ISO qui seront reparsées de l'autre côté.
Ici, c'est un appel de méthode.

### 3. Le temps reste injecté, mais par le JDK

La version hexagonale déclare un port `Clock` maison, avec `SystemClock` et `FixedClock` derrière.
Tomato refuse l'interface — mais la raison d'être du port reste entièrement valable : sans elle,
toutes les règles temporelles de ce POC deviennent intestables autrement qu'en attendant.

Il se trouve que le JDK fournit déjà l'abstraction : `java.time.Clock`, avec `systemUTC()` pour la
production et `fixed()` pour les tests. On injecte celle-là
([`ClockProducer`](src/main/java/fr/dllv/booking/config/ClockProducer.java)), et une règle ArchUnit
interdit tout appel à `OffsetDateTime.now()` pour que personne ne contourne l'injection.

C'est la lecture que je fais de « *embrace the framework* » : la bonne question n'est pas « est-ce
une interface ? » mais « est-ce **moi** qui l'écris ? ».

### 4. Les tests d'architecture remplacent les barrières de types

Un port hexagonal empêche *physiquement* le domaine de voir l'infrastructure : le compilateur
refuse. Sans port, rien ne l'empêche — alors
[`ArchitectureTest`](src/test/java/fr/dllv/booking/architecture/ArchitectureTest.java) le vérifie.
Neuf règles, chacune rattachée à une phrase de la documentation de tomato : les contrôleurs ne
touchent pas la persistance, les fonctionnalités ne se parlent que par leur package racine,
personne ne lit l'horloge système, le cœur ne connaît pas HTTP, aucun cycle entre les blocs.

## Stack

Quarkus 3.39, Java 21, Hibernate ORM + Panache, Flyway, PostgreSQL, Picocli, React + Vite +
Tailwind (le même frontend que les autres POC), JUnit 5 + AssertJ + Mockito + REST Assured +
ArchUnit, Spotless + Palantir Java Format, Docker.

Java, Quarkus, Maven, PostgreSQL, Flyway, Testcontainers, ArchUnit, Spotless et Palantir sont les
choix nommés par la page [Recommendations](https://tomato-architecture.github.io/recommendations/)
de tomato. Ce POC les suit à la lettre, pour que la comparaison porte sur l'architecture et pas
sur un désaccord d'outillage.

## Lancer

```bash
docker compose up --build
```

- API : http://localhost:8100
- Front : http://localhost:5180
- Mails (Mailpit) : http://localhost:8030

Comptes de démo (mot de passe `password`) : `alice@booking.test`, `bob@booking.test`,
`carol@booking.test`.

Les ports sont décalés par rapport aux versions Laravel / Symfony / Go / hexa-Quarkus pour pouvoir
en lancer plusieurs en même temps. Ils se règlent dans `docker-compose.yml`.

Contrairement aux POC hexagonaux, le `Dockerfile` construit le jar lui-même (image en deux étapes),
donc un clone suffit — pas besoin de lancer `mvn package` avant.

### La CLI, dans le même jar

Les mêmes cas d'usage, depuis un terminal :

```bash
docker compose run --rm app booking:expire-pending
docker compose run --rm app booking:create <uuid-ressource> <uuid-participant> \
    2026-12-01T10:00:00Z 2026-12-01T11:00:00Z --waitlist
```

Un conteneur jetable, et pas `docker compose exec app` : Quarkus démarre son serveur HTTP avant
d'exécuter la commande, donc dans le conteneur qui sert déjà l'API le port 8080 est pris et le
démarrage échoue.

### Le scénario à regarder

Le jeu de démonstration met Everest en salle complète demain de 11 h à 12 h, avec Bob en liste
d'attente sur le même créneau. Connectez-vous en Alice, annulez cette réservation : Bob est promu
automatiquement et reçoit un mail, visible dans Mailpit.

À noter : la demande de Bob **expire au bout de quinze minutes** comme toute demande non confirmée
— `Booking.isExpirable` ne fait pas d'exception pour la liste d'attente. C'est discutable, et c'est
le comportement des POC hexagonaux, qu'on garde à l'identique pour que la comparaison reste
honnête. Pour rejouer le scénario : `docker compose down -v && docker compose up`.

## Développement local (sans Docker)

```bash
./mvnw quarkus:dev
```

Quarkus provisionne un PostgreSQL éphémère (Dev Services / Testcontainers) tant qu'aucune URL de
datasource n'est configurée — rien à lancer à la main, mais il faut Docker disponible.

## Tests

```bash
./mvnw verify
```

Une commande, les quatre niveaux que tomato demande, plus le formatage :

- **domaine pur** — `BookingTest`, `TimeSlotTest`, `BookingStatusTest` : on instancie des classes,
  rien ne démarre, c'est instantané ;
- **service avec doublures** — `BookingServiceTest` : Mockito, et pas un repository en mémoire.
  C'est une consigne explicite de tomato (« *Prefer Mockito over an in-memory repository* ») ; la
  version hexagonale maintient pour cela un `InMemoryBookingRepository` de cent lignes, qui est une
  seconde implémentation à tenir à jour ;
- **persistance réelle** — `BookingRepositoryTest` : sur un vrai PostgreSQL, parce que c'est le seul
  niveau qui peut vérifier que le chevauchement écrit en SQL dit la même chose que celui écrit en
  Java. Deux formulations de la même règle dans deux langages, qui peuvent diverger sans que rien ne
  le signale ;
- **fonctionnalité entière** — `BookingApiTest` : le serveur tourne, la base tourne, les requêtes
  partent par le réseau avec un vrai jeton. Plus `DemoDataMigrationTest`, qui relit le jeu de
  démonstration par l'application — puisqu'il est inséré en SQL, il n'a traversé aucune règle ;
- **architecture** — `ArchitectureTest` : les neuf règles ci-dessus.

`mvn verify` fait aussi échouer la construction sur un code non formaté (`mvn spotless:apply` pour
corriger).

## Organisation

```
src/main/java/fr/dllv/booking/
  Main.java              point d'entrée : API sans argument, CLI avec
  common/                enveloppe JSON, erreurs, parsing de dates
  config/                réglages métier, horloge, traduction des erreurs en HTTP
  auth/                  comptes et jetons
  catalog/               ressources réservables
  bookings/              les réservations — le cœur
```

Et dans chaque fonctionnalité, les quatre rôles que nomme la page
[Concepts](https://tomato-architecture.github.io/concepts/) de tomato :

```
bookings/
  Booking.java           l'objet métier, avec ses règles
  BookingStatus.java     la machine à états
  TimeSlot.java          le créneau
  BookingService.java    l'API publique du module : un cas d'usage par méthode
  *Exception.java        les erreurs métier, chacune portant son statut HTTP
  *Dto.java              ce qui sort en JSON
  persistence/           les requêtes, et rien d'autre
  web/                   les contrôleurs, qui extraient et délèguent
  jobs/                  la tâche planifiée
  cli/                   les commandes
  mail/                  le contenu des notifications
```

**Le package racine est l'API publique du module ; les sous-packages sont son intérieur.** Une
fonctionnalité qui a besoin d'une autre passe par sa racine — `bookings` appelle `CatalogService` et
lit `AuthenticatedUser`, jamais leurs repositories. C'est la règle que tomato formule par « *each
module should expose only its public APIs* », et `ArchitectureTest` la fait respecter.

## Ce que je n'ai pas tranché

Deux endroits où tomato laisse le choix et où j'ai décidé seul, en expliquant dans le code :

- **`CatalogService` et `BookingService` sont dans le package racine**, pas dans un sous-package
  `domain/` comme dans [l'implémentation de
  référence](https://github.com/tomato-architecture/tomato-architecture-spring-boot-demo). Celle-ci
  ajoute en plus un `CatalogAPI` en façade devant son `CatalogService`, ce qui est précisément
  l'indirection que tomato reproche à l'hexagonal. J'ai gardé le principe — la racine est l'API — en
  retirant la délégation.
- **`BookingMailer` existe** au lieu d'un `mailer.send()` posé dans le service. Ce n'est pas une
  abstraction du framework : la classe ne cache pas `Mailer`, elle regroupe le contenu des messages.
  Le service décide *quand* notifier, ce fichier décide *ce qu'on écrit*.

## Les autres POC de la famille

- [poc-hexa-quarkus](https://github.com/xPiston/poc-hexa-quarkus) — même stack, architecture
  hexagonale : **c'est la comparaison qui compte**
- [poc-hexa-symfony](https://github.com/xPiston/poc-hexa-symfony) — PHP / Symfony
- [poc-hexa-go](https://github.com/xPiston/poc-hexa-go) — Go
- [poc-hexa-adonis](https://github.com/xPiston/poc-hexa-adonis) — TypeScript / AdonisJS
- [poc-hexa-laravel](https://github.com/xPiston/poc-hexa-laravel) — l'original, PHP / Laravel
