package net.memezforbeanz.starminerodyssey.shipspawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.List;
import java.util.Map;

/**
 * One {@code data/<ns>/ship_spawns/*.json} entry: which templates spawn where, on what
 * grid, and how their blocks are remapped. Templates are placed as plain blocks and turn
 * into ships once a player gets close (see {@link ShipSpawner}).
 *
 * @param dimensions          dimensions the spawner runs in
 * @param placement           vanilla-style {@code random_spread} grid, in chunks
 * @param y                   inclusive range for the template's origin Y. With {@code layers}
 *                            set, this is just the first layer's range
 * @param layers              several Y ranges, each with its own independent grid (salt offset
 *                            per layer): for tall dimensions, so one is always vertically near
 * @param templates           weighted structure templates ({@code data/<ns>/structures/<path>.nbt})
 * @param blockRemap          block id replacements applied to the template palette on load, for
 *                            templates saved with blocks from mods that no longer exist. A
 *                            remapped block loses its saved block entity data (the old mod's)
 * @param entityRemap         entity id replacements for the template's entities and spawners
 * @param spawnAs             {@code blocks}: place plain blocks, swapped for a ship built from
 *                            the template once a player is within {@code assemble_distance}.
 *                            {@code ship}: spawn straight in as a ship (no far-away LOD)
 * @param randomRotation      give each placement one of the 24 cube orientations (any axis, 90-degree
 *                            steps), the same for its block copy and its ship. Entities can't be
 *                            rotated that way, so templates with entities should turn this off
 * @param assembleDistance    blocks (3D) from a player at which the block version becomes a ship.
 *                            Keep it under VS2's {@code shipLoadDistance} (128), or the ship is
 *                            invisible for a while after the blocks vanish
 * @param releaseDistance     blocks (3D) every player must be beyond before an untouched ship turns
 *                            back into blocks. Keep it past VS2's {@code shipUnloadDistance} (196)
 * @param recycleAfterSeconds how long past {@code release_distance} before that happens; 0 keeps
 *                            every ship. Turn off for templates with entities, which would duplicate
 * @param motion              optional movement for some of the spawned ships
 */
public record ShipSpawnDefinition(
        List<ResourceKey<Level>> dimensions,
        Placement placement,
        YRange y,
        List<YRange> layers,
        List<WeightedTemplate> templates,
        Map<ResourceLocation, ResourceLocation> blockRemap,
        Map<ResourceLocation, ResourceLocation> entityRemap,
        SpawnMode spawnAs,
        boolean randomRotation,
        double assembleDistance,
        double releaseDistance,
        int recycleAfterSeconds,
        Motion motion) {

    public static final Codec<ShipSpawnDefinition> CODEC = RecordCodecBuilder.<ShipSpawnDefinition>create(i -> i.group(
            Level.RESOURCE_KEY_CODEC.listOf().fieldOf("dimensions").forGetter(ShipSpawnDefinition::dimensions),
            Placement.CODEC.fieldOf("placement").forGetter(ShipSpawnDefinition::placement),
            YRange.CODEC.optionalFieldOf("y", new YRange(0, 0)).forGetter(ShipSpawnDefinition::y),
            YRange.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(ShipSpawnDefinition::layers),
            WeightedTemplate.CODEC.listOf().fieldOf("templates").forGetter(ShipSpawnDefinition::templates),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC)
                    .optionalFieldOf("block_remap", Map.of()).forGetter(ShipSpawnDefinition::blockRemap),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC)
                    .optionalFieldOf("entity_remap", Map.of()).forGetter(ShipSpawnDefinition::entityRemap),
            SpawnMode.CODEC.optionalFieldOf("spawn_as", SpawnMode.BLOCKS).forGetter(ShipSpawnDefinition::spawnAs),
            Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter(ShipSpawnDefinition::randomRotation),
            Codec.doubleRange(1, 4096).optionalFieldOf("assemble_distance", 96.0).forGetter(ShipSpawnDefinition::assembleDistance),
            Codec.doubleRange(1, 4096).optionalFieldOf("release_distance", 208.0).forGetter(ShipSpawnDefinition::releaseDistance),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("recycle_after_seconds", 10)
                    .forGetter(ShipSpawnDefinition::recycleAfterSeconds),
            Motion.CODEC.optionalFieldOf("motion", Motion.NONE).forGetter(ShipSpawnDefinition::motion)
    ).apply(i, ShipSpawnDefinition::new)).flatXmap(ShipSpawnDefinition::validate, ShipSpawnDefinition::validate);

    private static DataResult<ShipSpawnDefinition> validate(ShipSpawnDefinition def) {
        if (def.templates.isEmpty()) {
            return DataResult.error(() -> "ship spawn needs at least one template");
        }
        if (def.releaseDistance <= def.assembleDistance) {
            return DataResult.error(() -> "release_distance must be larger than assemble_distance");
        }
        return DataResult.success(def);
    }

    /**
     * One definition per layer, each on its own grid, ids suffixed {@code /layer_<n>}; just
     * this one without layers.
     */
    public Map<ResourceLocation, ShipSpawnDefinition> expandLayers(ResourceLocation id) {
        if (layers.isEmpty()) {
            return Map.of(id, this);
        }
        Map<ResourceLocation, ShipSpawnDefinition> result = new java.util.LinkedHashMap<>();
        for (int n = 0; n < layers.size(); n++) {
            Placement p = new Placement(placement.spacing, placement.separation, placement.salt + n * 7919,
                    placement.spreadType);
            result.put(id.withSuffix("/layer_" + n), new ShipSpawnDefinition(dimensions, p, layers.get(n), List.of(),
                    templates, blockRemap, entityRemap, spawnAs, randomRotation, assembleDistance, releaseDistance,
                    recycleAfterSeconds, motion));
        }
        return result;
    }

    public int totalWeight() {
        int total = 0;
        for (WeightedTemplate t : templates) {
            total += t.weight();
        }
        return total;
    }

    public enum SpawnMode implements StringRepresentable {
        BLOCKS("blocks"),
        SHIP("ship");

        public static final Codec<SpawnMode> CODEC = StringRepresentable.fromEnum(SpawnMode::values);

        private final String name;

        SpawnMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum MotionType implements StringRepresentable {
        /** Stays put. */
        NONE("none"),
        /**
         * Spins around a random axis, posed directly each tick instead of simulated: no
         * rigid-body integration, so far cheaper than a dynamic ship.
         */
        KINEMATIC("kinematic"),
        /** A normal dynamic ship, given a random spin and drift once players are around. */
        PHYSICS("physics");

        public static final Codec<MotionType> CODEC = StringRepresentable.fromEnum(MotionType::values);

        private final String name;

        MotionType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * @param chance        fraction of spawns that move (rolled per cell, so stable)
     * @param angularSpeed  degrees per second, around a random axis
     * @param linearSpeed   blocks per second, in a random direction ({@code physics} only)
     * @param randomTilt    start at a random 3D orientation. Only for {@code spawn_as: ship}: a
     *                      block-LOD structure can only face the four 90° directions, so it
     *                      would visibly snap when it turns into a ship
     */
    public record Motion(MotionType type, float chance, Range angularSpeed, Range linearSpeed, boolean randomTilt) {
        public static final Motion NONE = new Motion(MotionType.NONE, 0, new Range(0, 0), new Range(0, 0), false);

        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(i -> i.group(
                MotionType.CODEC.fieldOf("type").forGetter(Motion::type),
                Codec.floatRange(0, 1).optionalFieldOf("chance", 1.0F).forGetter(Motion::chance),
                Range.CODEC.optionalFieldOf("angular_speed", new Range(0, 0)).forGetter(Motion::angularSpeed),
                Range.CODEC.optionalFieldOf("linear_speed", new Range(0, 0)).forGetter(Motion::linearSpeed),
                Codec.BOOL.optionalFieldOf("random_tilt", false).forGetter(Motion::randomTilt)
        ).apply(i, Motion::new));
    }

    public record Range(float min, float max) {
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("min").forGetter(Range::min),
                Codec.FLOAT.fieldOf("max").forGetter(Range::max)
        ).apply(i, Range::new));

        public float sample(net.minecraft.util.RandomSource random) {
            return min + random.nextFloat() * (max - min);
        }
    }

    public record Placement(int spacing, int separation, int salt, RandomSpreadType spreadType) {
        public static final Codec<Placement> CODEC = RecordCodecBuilder.<Placement>create(i -> i.group(
                Codec.intRange(1, 4096).fieldOf("spacing").forGetter(Placement::spacing),
                Codec.intRange(0, 4096).fieldOf("separation").forGetter(Placement::separation),
                Codec.INT.fieldOf("salt").forGetter(Placement::salt),
                RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.TRIANGULAR).forGetter(Placement::spreadType)
        ).apply(i, Placement::new)).flatXmap(Placement::validate, Placement::validate);

        private static DataResult<Placement> validate(Placement p) {
            return p.spacing > p.separation
                    ? DataResult.success(p)
                    : DataResult.error(() -> "spacing must be larger than separation");
        }
    }

    public record YRange(int min, int max) {
        public static final Codec<YRange> CODEC = RecordCodecBuilder.<YRange>create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(YRange::min),
                Codec.INT.fieldOf("max").forGetter(YRange::max)
        ).apply(i, YRange::new)).flatXmap(YRange::validate, YRange::validate);

        private static DataResult<YRange> validate(YRange y) {
            return y.min <= y.max
                    ? DataResult.success(y)
                    : DataResult.error(() -> "y.min must not exceed y.max");
        }
    }

    public record WeightedTemplate(ResourceLocation id, int weight) {
        public static final Codec<WeightedTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(WeightedTemplate::id),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(WeightedTemplate::weight)
        ).apply(i, WeightedTemplate::new));
    }
}
