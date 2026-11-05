package fr.dllv.booking.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Lit une date ISO 8601 qu'elle porte un decalage horaire ou non.
 *
 * <p>Le frontend envoie tantot {@code 2026-07-10T10:00:00Z}, tantot
 * {@code 2026-07-10T10:00:00}. Sans decalage, on considere UTC -- le meme
 * comportement que le {@code new DateTimeImmutable($s)} des versions PHP de ce
 * POC, dont le frontend est repris.
 */
public final class IsoDateTime {

    private IsoDateTime() {}

    /**
     * @throws DateTimeParseException si la chaine n'est pas une date ISO 8601
     */
    public static OffsetDateTime parse(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        }
    }

    /** Comme {@link #parse}, mais rend {@code null} au lieu de lever. */
    public static OffsetDateTime parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
