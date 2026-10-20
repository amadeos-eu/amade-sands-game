package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.sprite.Rotations;
import eu.amadeos.gameengine.sprite.Sprite;
import eu.amadeos.games.sands.domain.BuildingType;
import eu.amadeos.games.sands.domain.Rules;
import eu.amadeos.games.sands.domain.Side;
import eu.amadeos.games.sands.domain.UnitType;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The pictures of {@link Art} as sprites, each drawn once at its size on the screen and then only copied.
 * What turns, the vehicles, the turrets, the shells, is drawn in {@value #TURNS} directions
 * ({@link Rotations}), for each side in its colour.
 */
// --8<-- [start:pictures]
final class Pictures {
    static final int TURNS = 32;
    /** A harvester's hopper is drawn empty, full and at three steps between. */
    static final int LOADS = 5;
    /** An explosion grows and fades in this many pictures, at each of {@link #BLASTS} sizes. */
    static final int FRAMES = 8;
    static final double[] BLASTS = {3.5, 7, 13};
    static final double[] PUFFS = {2.5, 4, 5.5, 7, 8.5};

    private final Map<String, Sprite> buildings = new HashMap<>();
    private final Map<String, Sprite> rubble = new HashMap<>();
    private final Map<String, Sprite> icons = new HashMap<>();
    private final Map<String, Rotations> vehicles = new HashMap<>();
    private final Map<String, Rotations> shadows = new HashMap<>();
    private final Map<String, Sprite> wrecks = new HashMap<>();
    private final Map<Side, Rotations> turrets = new EnumMap<>(Side.class);
    private final Map<Side, Rotations> heads = new EnumMap<>(Side.class);
    private final Map<Side, Rotations> airlifters = new EnumMap<>(Side.class);
    final Rotations airlifterShadow = Rotations.of(TURNS, 26, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.airlifterShadow(g);
    });
    final Rotations shell = Rotations.of(TURNS, 16, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.shell(g);
    });
    final Rotations rocket = Rotations.of(TURNS, 18, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.rocket(g);
    });
    final Rotations flash = Rotations.of(TURNS, 20, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.flash(g);
    });
    final Sprite burrower = Sprite.drawn(44, 44, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.burrower(g, 22);
    });
    final Sprite burrowerSign = Sprite.drawn(32, 28, (g, w, h) -> {
        g.translate(w / 2, h / 2);
        Art.burrowerSign(g);
    });
    final Sprite grain = Sprite.drawn(10, 10, (g, w, h) -> Art.crystalGrain(g, 5, 5, 5));
    /** By size, then frame. */
    final Sprite[][] blasts = new Sprite[BLASTS.length][FRAMES];
    final Sprite[] puffs = new Sprite[PUFFS.length];
    // --8<-- [end:pictures]

    Pictures(Rules rules) {
        for (Side side : Side.values()) {
            Color[] team = Art.team(side);
            for (BuildingType type : rules.buildings()) {
                double w = type.width() * 16 + Art.MARGIN;
                double h = type.height() * 16 + Art.MARGIN;
                buildings.put(type.id() + side, Sprite.drawn(w, h,
                        (g, width, height) -> Art.building(g, type.id(), type.width(), type.height(), team)));
                icons.put(type.id() + side, Sprite.drawn(53, 35, (g, width, height) -> icon(g, type, team)));
            }
            for (UnitType type : rules.units()) {
                String id = type.id();
                double size = Art.size(id);
                if (type.harvests()) {
                    for (int load = 0; load < LOADS; load++) {
                        double part = load / (LOADS - 1.0);
                        vehicles.put(id + side + load, Rotations.of(TURNS, size, (g, w, h) -> {
                            g.translate(w / 2, h / 2);
                            Art.harvester(g, team, part);
                        }));
                    }
                } else {
                    vehicles.put(id + side, Rotations.of(TURNS, size, (g, w, h) -> {
                        g.translate(w / 2, h / 2);
                        switch (id) {
                            case "trike" -> Art.trike(g, team);
                            case "launcher" -> Art.launcher(g, team);
                            default -> Art.tankHull(g, team);
                        }
                    }));
                }
                icons.put(id + side, Sprite.drawn(53, 35, (g, w, h) -> {
                    g.translate(w / 2, h / 2 + 1);
                    g.scale(1.55, 1.55);
                    g.rotate(-0.5);
                    Art.vehicle(g, id, team, 0.3);
                }));
                icons.put("small " + id + side, Sprite.drawn(17, 17, (g, w, h) -> {
                    g.translate(w / 2, h / 2);
                    double k = 16 / size * 1.1;
                    g.scale(k, k);
                    g.rotate(-Math.PI / 2);
                    Art.vehicle(g, id, team, 0.3);
                }));
            }
            turrets.put(side, Rotations.of(TURNS, 30, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.tankTurret(g, team);
            }));
            heads.put(side, Rotations.of(TURNS, 30, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.turretHead(g, team);
            }));
            airlifters.put(side, Rotations.of(TURNS, 26, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.airlifter(g, team);
            }));
        }
        for (UnitType type : rules.units()) {
            String id = type.id();
            double size = Art.size(id);
            shadows.put(id, Rotations.of(TURNS, size, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.shadow(g, id);
            }));
            double[] hull = Art.hull(id);
            double s = Math.max(hull[0], hull[1]) * Art.U;
            wrecks.put(id, Sprite.drawn(2 * s, 2 * s, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.wreck(g, hull[0]);
            }));
        }
        for (BuildingType type : rules.buildings()) {
            double w = type.width() * 16;
            double h = type.height() * 16;
            rubble.put(type.id(), Sprite.drawn(w, h, (g, width, height) -> Art.rubble(g, w, h, type.id().hashCode())));
        }
        for (int size = 0; size < BLASTS.length; size++) {
            double r = BLASTS[size];
            for (int frame = 0; frame < FRAMES; frame++) {
                double t = frame / (double) FRAMES;
                double radius = r * (0.5 + 0.6 * t);
                double strength = 1 - t * t;
                long seed = size * 31L + frame;
                blasts[size][frame] = Sprite.drawn(4 * r, 4 * r, (g, w, h) -> {
                    Art.smooth(g);
                    Art.explosion(g, w / 2, h / 2, radius, strength, seed);
                });
            }
        }
        for (int i = 0; i < PUFFS.length; i++) {
            double r = PUFFS[i];
            puffs[i] = Sprite.drawn(2 * r, 2 * r, (g, w, h) -> {
                g.translate(w / 2, h / 2);
                Art.puff(g, r, 200);
            });
        }
    }

    /** A building in the panel, made smaller to fit. */
    private static void icon(java.awt.Graphics2D g, BuildingType type, Color[] team) {
        double w = type.width() * 16 + Art.MARGIN / 2;
        double h = type.height() * 16 + Art.MARGIN / 2;
        double k = Math.min(1.5, Math.min(48 / w, 31 / h));
        g.translate((53 - w * k) / 2, (35 - h * k) / 2 + 1);
        g.scale(k, k);
        Art.building(g, type.id(), type.width(), type.height(), team);
        if (type.weapon() != null) {
            g.translate(8, 8);
            g.rotate(-0.6);
            Art.turretHead(g, team);
        }
    }

    Sprite building(BuildingType type, Side side) {
        return buildings.get(type.id() + side);
    }

    Sprite rubble(BuildingType type) {
        return rubble.get(type.id());
    }

    /** What the panel shows for a building or a vehicle, 53 × 35. */
    Sprite icon(String id, Side side) {
        return icons.get(id + side);
    }

    /** A vehicle seen from above, facing up, for the panel's list of the chosen. */
    Sprite small(String id, Side side) {
        return icons.get("small " + id + side);
    }

    /** A vehicle's body; a harvester's with its hopper filled to {@code fill}, 0 to 1. */
    Rotations vehicle(UnitType type, Side side, double fill) {
        if (type.harvests()) {
            int load = (int) Math.round(Math.clamp(fill, 0, 1) * (LOADS - 1));
            return vehicles.get(type.id() + side + load);
        }
        return vehicles.get(type.id() + side);
    }

    Rotations shadow(UnitType type) {
        return shadows.get(type.id());
    }

    Sprite wreck(UnitType type) {
        return wrecks.get(type.id());
    }

    Rotations turret(Side side) {
        return turrets.get(side);
    }

    Rotations head(Side side) {
        return heads.get(side);
    }

    Rotations airlifter(Side side) {
        return airlifters.get(side);
    }

    /** The size of explosion that fits a radius in units. */
    static int blast(double radius) {
        if (radius < 5) return 0;
        return radius < 10 ? 1 : 2;
    }
}
