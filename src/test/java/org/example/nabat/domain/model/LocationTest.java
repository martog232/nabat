package org.example.nabat.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    @Test
    void shouldCreateLocationWithValidCoordinates() {
        Location location = Location.of(42.0, 23.0);

        assertEquals(42.0, location.latitude());
        assertEquals(23.0, location.longitude());
    }

    @Test
    void shouldCreateLocationWithBoundaryValues() {
        Location minLatMin = Location.of(-90.0, -180.0);
        Location maxLatMax = Location.of(90.0, 180.0);
        Location origin = Location.of(0.0, 0.0);

        assertEquals(-90.0, minLatMin.latitude());
        assertEquals(-180.0, minLatMin.longitude());
        assertEquals(90.0, maxLatMax.latitude());
        assertEquals(180.0, maxLatMax.longitude());
        assertEquals(0.0, origin.latitude());
        assertEquals(0.0, origin.longitude());
    }

    @Test
    void shouldThrowForInvalidLatitudeTooLow() {
        assertThrows(IllegalArgumentException.class, () -> Location.of(-90.1, 0.0));
    }

    @Test
    void shouldThrowForInvalidLatitudeTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> Location.of(90.1, 0.0));
    }

    @Test
    void shouldThrowForInvalidLongitudeTooLow() {
        assertThrows(IllegalArgumentException.class, () -> Location.of(0.0, -180.1));
    }

    @Test
    void shouldThrowForInvalidLongitudeTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> Location.of(0.0, 180.1));
    }
}
