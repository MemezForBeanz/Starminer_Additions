package net.memezforbeanz.starminerodyssey.gravity;

/** The field shapes the Gravity Generator can project, picked in its settings screen. */
public enum GeneratorFieldMode {
    /** Cubic field around the block sharing one gravity vector (side + angle). */
    DIRECTIONAL("directional"),
    /** Amethyst's wall field: a height×width×depth box out of one face, pulling toward it. */
    WALL("wall"),
    /** Amethyst's cylinder field: a ring around an axis, pulling toward (or off) the axis. */
    CYLINDER("cylinder"),
    /** Amethyst's planet field: a cube pulling toward the block from every side. */
    PLANET("planet");

    private static final GeneratorFieldMode[] VALUES = values();

    private final String serializedName;

    GeneratorFieldMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    /** Whether the mode exposes the attract/repel polarity toggle. */
    public boolean hasPolarity() {
        return this != DIRECTIONAL;
    }

    public GeneratorFieldMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static GeneratorFieldMode byName(String name) {
        for (GeneratorFieldMode mode : VALUES) {
            if (mode.serializedName.equals(name)) {
                return mode;
            }
        }
        return DIRECTIONAL;
    }

    public static GeneratorFieldMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DIRECTIONAL;
    }
}
