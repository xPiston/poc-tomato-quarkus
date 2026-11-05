package fr.dllv.booking.common;

import java.util.Map;

/**
 * Corps des reponses d'erreur, identique a celui des POC hexagonaux pour que le
 * meme frontend fonctionne sans modification : {@code error}, {@code message},
 * et {@code violations} quand il s'agit d'une validation de formulaire.
 */
public record ErrorResponse(String error, String message, Map<String, String> violations) {

    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }

    public static ErrorResponse validation(Map<String, String> violations) {
        return new ErrorResponse("ValidationError", "Les donnees envoyees sont invalides.", violations);
    }
}
