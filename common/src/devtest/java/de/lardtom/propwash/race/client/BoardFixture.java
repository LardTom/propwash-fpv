package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;

public final class BoardFixture {
    private BoardFixture() {
    }

    public static void show(RacePayloads.LeaderboardS2C payload) {
        ClientLeaderboards.apply(Minecraft.getInstance(), payload);
    }

    public static void clear() {
        ClientLeaderboards.clear();
    }
}
