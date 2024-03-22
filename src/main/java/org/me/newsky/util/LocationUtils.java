package org.me.newsky.util;

import org.bukkit.Location;

public class LocationUtils {

    /**
     * Parses a location string and returns a Location object.
     *
     * @param locationString The location string in the format "x,y,z,yaw,pitch".
     * @return The parsed Location object.
     */
    public static Location parseLocation(String locationString) {
        String[] parts = locationString.split(",");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        float yaw = Float.parseFloat(parts[3]);
        float pitch = Float.parseFloat(parts[4]);
        return new Location(null, x, y, z, yaw, pitch);
    }

    /**
     * Converts a Location object to a string.
     *
     * @param location The Location object to convert.
     * @return The string representation of the Location object.
     */
    public static String locationToString(Location location) {
        return location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getYaw() + "," + location.getPitch();
    }
}
