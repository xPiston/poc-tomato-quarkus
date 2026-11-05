package fr.dllv.booking.catalog;

/** Representation JSON d'une ressource reservable. */
public record ResourceDto(String id, String name, int capacity) {

    public static ResourceDto of(BookableResource resource) {
        return new ResourceDto(resource.id().toString(), resource.name(), resource.capacity());
    }
}
