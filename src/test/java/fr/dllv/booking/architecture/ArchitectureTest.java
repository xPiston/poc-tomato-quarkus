package fr.dllv.booking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Les regles de l'architecture, executees comme des tests.
 *
 * <p>Tomato recommande ArchUnit pour cette raison precise : une architecture
 * decrite dans un README est une intention, une architecture decrite en tests
 * est une contrainte. Chaque regle ci-dessous correspond a une phrase de la
 * documentation de tomato, et echoue si quelqu'un -- moi dans six mois, ou
 * n'importe qui d'autre -- la contredit sans le vouloir.
 *
 * <p>C'est aussi ce qui rend l'absence d'interfaces tenable. Un port hexagonal
 * empeche <em>physiquement</em> le domaine de voir l'infrastructure : le
 * compilateur refuse. Ici rien ne l'empeche physiquement, puisqu'il n'y a pas
 * de barriere de types -- ce sont ces tests qui tiennent le role, et c'est le
 * marche que tomato propose : moins de code, a condition de verifier ce que le
 * code ne dit plus tout seul.
 */
@AnalyzeClasses(packages = "fr.dllv.booking", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * "Web controllers should only extract necessary data from incoming
     * requests and pass it to the service layer."
     *
     * <p>Un controleur qui atteint un repository saute la couche de service, et
     * avec elle la transaction et les regles. C'est la regression la plus
     * facile a commettre et la plus difficile a voir en revue : elle ressemble a
     * une optimisation.
     */
    @ArchTest
    static final ArchRule les_controleurs_ne_touchent_pas_la_persistance = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..persistence..")
            .because("un controleur delegue au service, qui porte la transaction et les regles");

    /**
     * "Scheduled jobs should prepare the input and pass it to the service
     * layer." Meme regle, pour les deux autres entrees.
     */
    @ArchTest
    static final ArchRule les_taches_et_la_cli_ne_touchent_pas_la_persistance = noClasses()
            .that()
            .resideInAnyPackage("..jobs..", "..cli..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..persistence..")
            .because("une tache planifiee ou une commande est une entree comme une autre");

    /**
     * "Each module should expose only its public APIs and hide its internal
     * implementation details."
     *
     * <p>Le package racine d'une fonctionnalite est son API ; ses
     * sous-packages sont son interieur. Une fonctionnalite qui va chercher le
     * repository d'une autre se lie a son schema de donnees et plus a son
     * contrat.
     */
    @ArchTest
    static final ArchRule les_fonctionnalites_ne_se_touchent_que_par_leur_racine = noClasses()
            .that()
            .resideInAPackage("fr.dllv.booking.bookings..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "fr.dllv.booking.catalog.persistence..",
                    "fr.dllv.booking.catalog.web..",
                    "fr.dllv.booking.auth.persistence..",
                    "fr.dllv.booking.auth.web..",
                    "fr.dllv.booking.auth.security..")
            .because("les reservations parlent a CatalogService et a AuthenticatedUser, pas a leurs entrailles");

    /** La meme regle, dans l'autre sens : le catalogue ne connait pas les reservations. */
    @ArchTest
    static final ArchRule le_catalogue_ignore_les_reservations = noClasses()
            .that()
            .resideInAPackage("fr.dllv.booking.catalog..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("fr.dllv.booking.bookings..")
            .because("une ressource reservable existe sans qu'on sache ce qu'est une reservation");

    /** Et l'authentification ne connait ni l'un ni l'autre. */
    @ArchTest
    static final ArchRule l_authentification_ignore_le_metier = noClasses()
            .that()
            .resideInAPackage("fr.dllv.booking.auth..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("fr.dllv.booking.bookings..", "fr.dllv.booking.catalog..")
            .because("un compte ne sait pas a quoi l'application sert");

    /**
     * Le temps est une dependance, pas un appel statique.
     *
     * <p>Toutes les regles de ce POC sont temporelles -- expiration, penalite,
     * passe, horizon. Un seul {@code OffsetDateTime.now()} enfoui dans une
     * methode rend la regle qui l'entoure intestable autrement qu'en attendant.
     * Les POC hexagonaux obtiennent la meme garantie avec un port {@code Clock}
     * maison ; ici c'est le {@link java.time.Clock} du JDK, injecte, et cette
     * regle est ce qui empeche de contourner l'injection.
     */
    @ArchTest
    static final ArchRule personne_ne_lit_l_horloge_systeme_directement = noClasses()
            .should()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .because("l'horloge est injectee (java.time.Clock) pour que le temps soit un parametre de test");

    /**
     * "No unnecessary interfaces": pas de couple interface/implementation
     * ecrit a la main pour la persistance.
     *
     * <p>La regle se formule a l'envers de ce qu'on croit : ce qu'on interdit,
     * ce n'est pas d'ecrire une interface, c'est qu'un repository en soit une.
     * {@code PanacheRepositoryBase} vient du framework et ne compte pas.
     */
    @ArchTest
    static final ArchRule les_repositories_sont_des_classes = classes()
            .that()
            .resideInAPackage("..persistence..")
            .and()
            .haveSimpleNameEndingWith("Repository")
            .should()
            .notBeInterfaces()
            .because("une interface de repository avec une seule implementation est du ceremonial");

    /**
     * Le metier ne connait pas HTTP.
     *
     * <p>C'est la seule regle hexagonale que tomato garde telle quelle :
     * "Application Core should not depend on how it is invoked". Un objet
     * metier annote JAX-RS ne serait plus appelable depuis la CLI ni depuis la
     * tache planifiee.
     */
    @ArchTest
    static final ArchRule le_metier_ne_connait_pas_http = noClasses()
            .that()
            .resideInAnyPackage(
                    "fr.dllv.booking.bookings", "fr.dllv.booking.catalog", "fr.dllv.booking.auth", "..persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.ws.rs..", "io.vertx..", "io.quarkus.vertx..")
            .because("le coeur doit rester appelable depuis un main(), une commande ou une tache");

    /** Aucun cycle entre les grands blocs. */
    @ArchTest
    static final ArchRule aucun_cycle_entre_les_fonctionnalites = SlicesRuleDefinition.slices()
            .matching("fr.dllv.booking.(*)..")
            .should()
            .beFreeOfCycles();
}
