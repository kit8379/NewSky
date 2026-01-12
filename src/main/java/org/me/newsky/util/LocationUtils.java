package org.me.newsky.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class LocationUtils {

    /**
     * Parses a location string and returns a Location object.
     *
     * @param locationString The location string in the format "x,y,z,yaw,pitch".
     * @return The parsed Location object.
     */
    public static Location stringToLocation(String worldName, String locationString) {
        String[] parts = locationString.split(",");
        World world = Bukkit.getWorld(worldName);
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        float yaw = Float.parseFloat(parts[3]);
        float pitch = Float.parseFloat(parts[4]);
        return new Location(world, x, y, z, yaw, pitch);
    }
}