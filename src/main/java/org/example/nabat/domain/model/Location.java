package org.example.nabat.domain.model;

/**
 * A WGS-84 coordinate pair.
 *
 * <p>Validation lives in the compact constructor, so <em>every</em> way of creating a
 * {@code Location} is checked. It used to sit only in the {@link #of} factory, which
 * a record's implicit canonical constructor bypasses entirely — {@code new
 * Location(999, 999)} compiled and produced an invalid value.
 */
public record Location(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public Location {
        if (latitude < -90 || latitude > 90 || Double.isNaN(latitude)) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude);
        }
        if (longitude < -180 || longitude > 180 || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude);
        }
    }

    public static Location of(double latitude, double longitude) {
        return new Location(latitude, longitude);
    }

    /**
     * Great-circle distance in kilometres.
     *
     * <p>Retained for in-memory checks (the frontend does the same thing client-side).
     * Radius <em>queries</em> are pushed into the database — PostGIS {@code ST_DWithin}
     * where available, an equivalent SQL expression otherwise — since filtering in Java
     * would mean loading every alert.
     */
    public double distanceTo(Location other) {
        double latDistance = Math.toRadians(other.latitude - this.latitude);
        double lonDistance = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                   + Math.cos(Math.toRadians(this.latitude))
                     * Math.cos(Math.toRadians(other.latitude))
                     * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
