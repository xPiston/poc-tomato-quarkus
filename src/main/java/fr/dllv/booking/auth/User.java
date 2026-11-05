package fr.dllv.booking.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Un compte.
 *
 * <p>Il porte un {@code attendeeId} distinct de son {@code id} : c'est sous ce
 * second identifiant que la fonctionnalite reservation connait les gens. Elle
 * n'a jamais vu cette classe et n'a aucune raison de la voir -- une
 * reservation appartient a un participant, pas a un utilisateur authentifie.
 * Ce sont deux notions qui se trouvent coincider aujourd'hui.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "attendee_id", nullable = false, unique = true)
    private UUID attendeeId;

    @Column(length = 180, nullable = false, unique = true)
    private String email;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 255, nullable = false)
    private String password;

    /** Requis par JPA. */
    protected User() {}

    public UUID id() {
        return id;
    }

    public UUID attendeeId() {
        return attendeeId;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    /** L'empreinte bcrypt, jamais le mot de passe. */
    public String passwordHash() {
        return password;
    }
}
