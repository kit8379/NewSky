// UpgradeResult.java
package org.me.newsky.model;

public final class UpgradeResult {

    private final String upgradeId;
    private final int oldLevel;
    private final int newLevel;
    private final int requireIslandLevel;

    public UpgradeResult(String upgradeId, int oldLevel, int newLevel, int requireIslandLevel) {
        this.upgradeId = upgradeId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.requireIslandLevel = requireIslandLevel;
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
}
