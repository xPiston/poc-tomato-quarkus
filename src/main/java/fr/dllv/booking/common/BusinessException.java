package fr.dllv.booking.common;

/**
 * Racine des erreurs METIER, c'est-a-dire de celles qu'un utilisateur peut
 * declencher en se servant normalement de l'application : un creneau deja pris,
 * une annulation trop tardive, une reservation inexistante.
 *
 * Chaque exception porte elle-meme son statut HTTP. Ce n'est pas une fuite de
 * la couche web dans le metier, c'est le refus d'une table de correspondance :
 * la version hexagonale de ce POC maintenait une {@code Map<Class, Integer>}
 * dans son mapper, qu'il fallait penser a completer a chaque nouvelle
 * exception, sans que rien ne le rappelle. Ici l'auteur de l'exception declare
 * la reponse au moment ou il declare l'erreur, et
 * {@code BusinessExceptionMapper} n'a plus rien a savoir.
 */
public abstract class BusinessException extends RuntimeException {

    /** 409 : la demande est comprehensible mais entre en conflit avec l'etat actuel. */
    protected static final int CONFLICT = 409;

    /** 422 : la demande est bien formee mais viole une regle de validite. */
    protected static final int UNPROCESSABLE = 422;

    /** 404 : l'objet designe n'existe pas. */
    protected static final int NOT_FOUND = 404;

    private final int status;

    protected BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Nom court de l'exception, repris tel quel dans le champ {@code error} du JSON. */
    public String code() {
        return getClass().getSimpleName();
    }
}
