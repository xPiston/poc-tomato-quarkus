package fr.dllv.booking.bookings.mail;

import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.config.BookingProperties;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.format.DateTimeFormatter;

/**
 * Les emails que la fonctionnalite reservation envoie.
 *
 * <p>Pas d'interface {@code Notifier} avec un {@code MailerNotifier} et un
 * {@code LogNotifier} derriere, comme dans les POC hexagonaux : tomato dit de
 * ne pas abstraire le framework "au cas ou" on changerait de canal, et
 * {@code Mailer} est deja une abstraction -- celle de Quarkus, qui sait parler
 * SMTP en production et se taire sous test
 * ({@code quarkus.mailer.mock=true}).
 *
 * <p>Reste que cette classe existe au lieu d'un {@code mailer.send()} pose au
 * milieu du service, et ce n'est pas une contradiction : elle ne cache pas le
 * framework, elle regroupe le contenu des messages. Le service decide
 * <em>quand</em> notifier, ce fichier decide <em>ce qu'on ecrit</em>, et les
 * tests du service le mockent pour verifier le quand sans fabriquer de mail.
 */
@ApplicationScoped
public class BookingMailer {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Mailer mailer;
    private final BookingProperties properties;

    public BookingMailer(Mailer mailer, BookingProperties properties) {
        this.mailer = mailer;
        this.properties = properties;
    }

    /** Une place s'est liberee, la demande en attente devient une reservation. */
    public void notifyWaitlistPromotion(Booking booking) {
        send(
                booking,
                "Bonne nouvelle : vous etes confirme",
                "Une place s'est liberee : votre reservation %s (creneau du %s) est confirmee."
                        .formatted(booking.id(), booking.slot().startsAt().format(DISPLAY_FORMAT)));
    }

    /** Rappel a l'approche du creneau. */
    public void remindUpcoming(Booking booking) {
        send(
                booking,
                "Rappel : votre creneau approche",
                "Votre reservation %s debute le %s."
                        .formatted(booking.id(), booking.slot().startsAt().format(DISPLAY_FORMAT)));
    }

    /**
     * Le participant n'est qu'un identifiant opaque pour cette fonctionnalite,
     * d'ou une adresse de demonstration derivee de l'identifiant. Un vrai
     * systeme demanderait l'adresse a la fonctionnalite qui gere les comptes.
     */
    private void send(Booking booking, String subject, String body) {
        mailer.send(Mail.withText("%s@booking.test".formatted(booking.attendeeId()), subject, body)
                .setFrom(properties.mailFrom()));
    }
}
