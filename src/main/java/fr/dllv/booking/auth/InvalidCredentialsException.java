package fr.dllv.booking.auth;

import fr.dllv.booking.common.BusinessException;

/**
 * Email inconnu ou mot de passe faux.
 *
 * <p>Un seul message pour les deux cas : distinguer renseignerait un attaquant
 * sur les adresses qui existent.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(UNPROCESSABLE, "Identifiants invalides.");
    }
}
