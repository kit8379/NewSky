package org.me.newsky.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.me.newsky.exceptions.WorldNotFoundException;

public class LocationUtils {

    /**
     * Parses a location string and returns a Location object.
     *
     * @param locationString The location string in the format "x,y,z,yaw,pitch".
     * @return The parsed Location object.
     * @throws WorldNotFoundException if the world is not loaded on this server.
     */
    public static Location stringToLocation(String worldName, String locationString) {
        String[] parts = locationString.split(",");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new WorldNotFoundException();
        }
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);
        float yaw = Float.parseFloat(parts[3]);
        float pitch = Float.parseFloat(parts[4]);
        return new Location(world, x, y, z, yaw, pitch);
    }
}