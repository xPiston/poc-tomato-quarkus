package fr.dllv.booking.common;

/**
 * Enveloppe {@code { "data": ... }} des reponses JSON.
 *
 * Elle existe parce que le frontend React est partage avec les autres POC et
 * attend ce contrat ; pas parce qu'une couche l'impose.
 */
public record ApiEnvelope<T>(T data) {}
