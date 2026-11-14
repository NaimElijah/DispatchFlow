package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.InvalidCoordinates;

/**
 * A point on the earth, in WGS 84 degrees (DL §3.3): a latitude from -90 to 90 and a longitude from
 * -180 to 180, both inclusive. {@link #toString()} hides them.
 *
 * @param latitude degrees north of the equator
 * @param longitude degrees east of Greenwich
 */
public record GeoPoint(double latitude, double longitude) {

    private static final double MAX_LATITUDE = 90;
    private static final double MAX_LONGITUDE = 180;

    /**
     * Checks the coordinates.
     *
     * @throws RuleViolation with {@link InvalidCoordinates} when either is out of range or not a
     *     number
     */
    public GeoPoint {
        // Every comparison with NaN is false, so the negated range check refuses NaN too.
        if (!(Math.abs(latitude) <= MAX_LATITUDE && Math.abs(longitude) <= MAX_LONGITUDE)) {
            throw new RuleViolation(new InvalidCoordinates());
        }
    }

    /**
     * Creates the point.
     *
     * @param latitude degrees north of the equator
     * @param longitude degrees east of Greenwich
     * @return the point
     * @throws RuleViolation with {@link InvalidCoordinates} when either is out of range or not a
     *     number
     */
    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    /**
     * A form that hides the coordinates, because a customer's location is personal data (NFR-19).
     *
     * @return {@code GeoPoint[redacted]}
     */
    @Override
    public String toString() {
        return "GeoPoint[redacted]";
    }
}
