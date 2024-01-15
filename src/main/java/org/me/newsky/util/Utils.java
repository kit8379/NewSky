package org.me.newsky.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");

    public static String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String color = matcher.group(1);
            StringBuilder minecraftHex = new StringBuilder("§x");
            for (char c : color.toCharArray()) {
                minecraftHex.append("§").append(c);
            }
            matcher.appendReplacement(buffer, minecraftHex.toString());
        }
        return matcher.appendTail(buffer).toString();
    }

    public static String colorize(String message) {
        String translatedHex = translateHexColorCodes(message);
        return translatedHex.replaceAll("&([0-9a-fk-or])", "§$1");
    }
}