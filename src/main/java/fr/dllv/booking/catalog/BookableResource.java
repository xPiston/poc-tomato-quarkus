package fr.dllv.booking.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Une chose que l'on peut reserver : une salle, un praticien, du materiel.
 *
 * <p>{@code capacity} est le nombre de reservations confirmees qui peuvent
 * coexister sur des creneaux qui se chevauchent. Une salle de reunion vaut 1,
 * un open space partage vaut N. C'est ce seul parametre qui garde le domaine
 * generique.
 *
 * <p>La regle de capacite vit ici et pas dans le service : savoir s'il reste de
 * la place ne depend que de l'etat de la ressource et d'un decompte qu'on lui
 * presente. {@code CatalogService} n'aurait rien a y ajouter, et tomato
 * demande explicitement que ce genre de calcul reste dans l'objet concerne.
 *
 * <p>Noter que {@link #hasRoomFor} prend un entier, pas une liste de
 * reservations. C'est ce qui empeche le catalogue de dependre de la
 * fonctionnalite reservation : le decompte est fait par qui sait compter -- une
 * requete {@code count} du cote {@code bookings} -- et la decision est prise
 * par qui connait la capacite. Sans ca, les deux modules se tiendraient
 * mutuellement et la regle "chaque module n'expose que son API publique"
 * n'aurait plus de sens.
 */
@Entity
@Table(name = "resources")
public class BookableResource {

    @Id
    private UUID id;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    /** Requis par JPA. */
    protected BookableResource() {}

    private BookableResource(String name, int capacity) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.capacity = capacity;
    }

    public static BookableResource of(String name, int capacity) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom d'une ressource ne peut pas etre vide.");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("La capacite d'une ressource doit valoir au moins 1.");
        }

        return new BookableResource(name, capacity);
    }

    /**
     * Reste-t-il de la place, sachant qu'il y a deja {@code alreadyConfirmed}
     * reservations confirmees sur le creneau vise ?
     */
    public boolean hasRoomFor(long alreadyConfirmed) {
        return alreadyConfirmed < capacity;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }
}
