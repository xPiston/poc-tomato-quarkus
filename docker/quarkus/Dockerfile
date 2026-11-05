# Image JVM de l'application.
#
# Construction en deux etapes, contrairement aux POC hexagonaux de la famille
# dont le Dockerfile copie `target/` : la, il faut avoir lance `mvn package` a
# la main avant le `docker compose up --build`, et l'image echoue sans dire
# pourquoi sur un depot fraichement clone. Ici le jar est construit dans
# l'image, donc un clone suffit.

# --- Etape 1 : construction -------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Le pom seul d'abord, pour que le telechargement des dependances reste en
# cache tant que les dependances ne changent pas.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Les tests tournent dans la CI et en local (`./mvnw test`), pas ici : ils ont
# besoin de Docker pour leur PostgreSQL, et on est deja dedans.
RUN mvn -B -q package -DskipTests

# --- Etape 2 : execution ----------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24

ENV LANGUAGE='en_US:en'

# Quatre couches : les bibliotheques, peu changeantes, restent en cache meme
# quand seul le code applicatif bouge.
COPY --from=build --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

# Sans argument : l'API et la tache planifiee.
# Avec un argument : la commande, puis on sort.
#
#   docker compose run --rm app booking:expire-pending
#
# Un conteneur jetable, et pas `docker compose exec app`, parce que Quarkus
# demarre son serveur HTTP avant d'executer la commande : dans le conteneur qui
# sert deja l'API, le port 8080 est pris et le demarrage echoue.
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
