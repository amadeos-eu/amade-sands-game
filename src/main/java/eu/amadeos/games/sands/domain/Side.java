package eu.amadeos.games.sands.domain;

import java.util.Locale;

/** The two houses on the planet: blue is the player's, red the computer's. In the title both play themselves. */
public enum Side {
    BLUE, RED;

    public Side enemy() {
        return this == BLUE ? RED : BLUE;
    }

    /** As the maps write it: {@code blue} or {@code red}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The side a map names, or {@code null}. */
    static Side ofId(String id) {
        for (Side side : values()) if (side.id().equals(id)) return side;
        return null;
    }
}
