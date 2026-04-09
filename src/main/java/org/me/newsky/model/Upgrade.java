package org.me.newsky.model;

public final class Upgrade {

    private final String upgradeId;
    private final int oldLevel;
    private final int newLevel;
    private final int requireIslandLevel;
    private final double price;

    public Upgrade(String upgradeId, int oldLevel, int newLevel, int requireIslandLevel, double price) {
        this.upgradeId = upgradeId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.requireIslandLevel = requireIslandLevel;
        this.price = price;
    }

    public String getUpgradeId() {
        return upgradeId;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public int getRequireIslandLevel() {
        return requireIslandLevel;
    }

    public double getPrice() {
        return price;
    }
}