package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.data.Catalog;
import eu.amadeos.gameengine.data.TextData;
import eu.amadeos.gameengine.data.TextData.DataException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the data files say: the vehicles of {@code units.txt}, the buildings of {@code buildings.txt},
 * the missions of {@code missions.txt} and the computer's plan in {@code commander.txt}. Read once; a mistake
 * in a file stops the game at the start with the file and the line.
 */
public final class Rules {
    /** Where the data files lie among the classes. */
    public static final String FOLDER = "/eu/amadeos/games/sands/data/";

    private final Map<String, UnitType> units;
    private final Map<String, BuildingType> buildings;
    private final Map<Integer, Mission> missions;
    private final Plan plan;

    private Rules(Map<String, UnitType> units, Map<String, BuildingType> buildings, Map<Integer, Mission> missions,
                  Plan plan) {
        this.units = units;
        this.buildings = buildings;
        this.missions = missions;
        this.plan = plan;
    }

    /** The rules of the game, from its data files. */
    public static Rules load() {
        return read(TextData.load(Rules.class, FOLDER + "units.txt"), TextData.load(Rules.class, FOLDER + "buildings.txt"),
                TextData.load(Rules.class, FOLDER + "missions.txt"), TextData.load(Rules.class, FOLDER + "commander.txt"));
    }

    public static Rules read(List<TextData.Line> unitLines, List<TextData.Line> buildingLines,
                             List<TextData.Line> missionLines, List<TextData.Line> planLines) {
        Map<String, BuildingType> buildings = buildings(buildingLines);
        Map<String, UnitType> units = units(unitLines, buildings);
        Plan plan = plan(planLines, units, buildings);
        return new Rules(units, buildings, missions(missionLines), plan);
    }

    // --8<-- [start:units]
    private static Map<String, UnitType> units(List<TextData.Line> lines, Map<String, BuildingType> buildings) {
        Map<String, UnitType> units = new LinkedHashMap<>();
        for (Catalog.Entry unit : Catalog.read(lines, "unit")) {
            UnitType type = new UnitType(unit.name(), positive(unit, "health"), share(unit, "armour"),
                    positive(unit, "speed"), positive(unit, "sight"), positive(unit, "size"), unit.integer("cost"),
                    positive(unit, "seconds"), needs(unit, buildings), weapon(unit), yes(unit, "turret"),
                    unit.integer("capacity", 0));
            unit.finish();
            units.put(type.id(), type);
        }
        return units;
    }
    // --8<-- [end:units]

    private static Map<String, BuildingType> buildings(List<TextData.Line> lines) {
        List<Catalog.Entry> entries = Catalog.read(lines, "building");
        Map<String, BuildingType> buildings = new LinkedHashMap<>();
        for (Catalog.Entry building : entries) {
            TextData.Line size = building.line("size");
            if (size.size() != 3) throw size.error("size takes the width and the height in tiles");
            int width = size.integer(1);
            int height = size.integer(2);
            if (width < 1 || height < 1 || width > 6 || height > 6) throw size.error("a building is 1 to 6 tiles a side");
            int cost = building.integer("cost", 0);
            double seconds = cost > 0 ? positive(building, "seconds") : 1;
            int dock = building.integer("dock", -1);
            if (dock >= width) throw building.line("dock").error("the dock must be one of the building's columns");
            BuildingType type = new BuildingType(building.name(), width, height, positive(building, "health"),
                    share(building, "armour"), cost, seconds, building.integer("power", 0),
                    positive(building, "sight"), building.integer("storage", 0), List.of(), yes(building, "yard"),
                    yes(building, "factory"), yes(building, "radar"), dock, weapon(building));
            buildings.put(type.id(), type);
        }
        // The needs name other buildings, so they are read when all are known.
        for (Catalog.Entry building : entries) {
            BuildingType type = buildings.get(building.name());
            buildings.put(type.id(), new BuildingType(type.id(), type.width(), type.height(), type.health(),
                    type.armour(), type.cost(), type.seconds(), type.power(), type.sight(), type.storage(),
                    needs(building, buildings), type.yard(), type.factory(), type.radar(), type.dock(), type.weapon()));
            building.finish();
        }
        if (buildings.values().stream().noneMatch(BuildingType::yard)) {
            throw new DataException("buildings.txt: no building is the yard (yard yes)");
        }
        return buildings;
    }

    // --8<-- [start:weapon]
    /** The weapon of a unit or a turret: {@code weapon gun} or {@code weapon rocket} and its numbers. */
    private static Weapon weapon(Catalog.Entry entry) {
        if (!entry.has("weapon")) return null;
        String word = entry.text("weapon");
        Weapon.Kind kind;
        try {
            kind = Weapon.Kind.valueOf(word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw entry.line("weapon").error('"' + word + "\" is not a weapon; use gun or rocket");
        }
        double splash = kind == Weapon.Kind.ROCKET ? positive(entry, "splash") : 0;
        return new Weapon(kind, positive(entry, "range"), positive(entry, "damage"), positive(entry, "reload"),
                positive(entry, "shot"), splash);
    }
    // --8<-- [end:weapon]

    private static List<String> needs(Catalog.Entry entry, Map<String, BuildingType> buildings) {
        if (!entry.has("needs")) return List.of();
        TextData.Line line = entry.line("needs");
        List<String> needs = new ArrayList<>();
        for (int i = 1; i < line.size(); i++) {
            String id = line.word(i);
            if (id.equals("none") && line.size() == 2) return List.of();
            if (!buildings.containsKey(id)) throw line.error('"' + id + "\" is not a building");
            needs.add(id);
        }
        return needs;
    }

    private static Map<Integer, Mission> missions(List<TextData.Line> lines) {
        Map<Integer, Mission> missions = new LinkedHashMap<>();
        for (Catalog.Entry mission : Catalog.read(lines, "mission")) {
            int number;
            try {
                number = Integer.parseInt(mission.name());
            } catch (NumberFormatException e) {
                throw mission.error("a mission is a number");
            }
            if (number != missions.size() + 1) throw mission.error("the missions are numbered 1, 2, 3 and so on");
            missions.put(number, new Mission(number, mission.text("map"), mission.integer("credits"),
                    mission.integer("enemy"), yes(mission, "burrower"), positive(mission, "attack"),
                    positive(mission, "every"), mission.integer("army"), mission.integer("grows"),
                    mission.integer("turrets")));
            mission.finish();
        }
        if (missions.isEmpty()) throw new DataException("missions.txt: no mission");
        return missions;
    }

    private static Plan plan(List<TextData.Line> lines, Map<String, UnitType> units,
                             Map<String, BuildingType> buildings) {
        List<Catalog.Entry> entries = Catalog.read(lines, "plan");
        if (entries.size() != 1) throw new DataException("commander.txt: one plan, please");
        Catalog.Entry plan = entries.getFirst();
        List<BuildingType> build = new ArrayList<>();
        TextData.Line order = plan.line("build");
        for (int i = 1; i < order.size(); i++) {
            BuildingType type = buildings.get(order.word(i));
            if (type == null || !type.buildable()) throw order.error('"' + order.word(i) + "\" is not a building to build");
            build.add(type);
        }
        List<UnitType> mix = new ArrayList<>();
        TextData.Line army = plan.line("mix");
        for (int i = 1; i < army.size(); i++) {
            UnitType type = units.get(army.word(i));
            if (type == null || !type.armed()) throw army.error('"' + army.word(i) + "\" is not an armed vehicle");
            mix.add(type);
        }
        if (mix.isEmpty()) throw army.error("the army needs at least one vehicle");
        Plan result = new Plan(build, mix, plan.integer("harvesters"));
        plan.finish();
        return result;
    }

    private static double positive(Catalog.Entry entry, String key) {
        double value = entry.number(key);
        if (value <= 0) throw entry.line(key).error(key + " must be more than 0");
        return value;
    }

    /** A part, from 0 up to but not including 1; 0 if not given. */
    private static double share(Catalog.Entry entry, String key) {
        double value = entry.number(key, 0);
        if (value < 0 || value >= 1) throw entry.line(key).error(key + " is a part, from 0 to below 1");
        return value;
    }

    private static boolean yes(Catalog.Entry entry, String key) {
        String word = entry.text(key, "no");
        return switch (word) {
            case "yes" -> true;
            case "no" -> false;
            default -> throw entry.line(key).error(key + " is yes or no");
        };
    }

    public UnitType unit(String id) {
        UnitType unit = units.get(id);
        if (unit == null) throw new IllegalArgumentException("no unit " + id);
        return unit;
    }

    public BuildingType building(String id) {
        BuildingType building = buildings.get(id);
        if (building == null) throw new IllegalArgumentException("no building " + id);
        return building;
    }

    /** Whether {@code id} names a unit or a building; for the maps. */
    public boolean isUnit(String id) {
        return units.containsKey(id);
    }

    public boolean isBuilding(String id) {
        return buildings.containsKey(id);
    }

    /** All vehicles, in the order of the file. */
    public List<UnitType> units() {
        return List.copyOf(units.values());
    }

    /** All buildings, in the order of the file. */
    public List<BuildingType> buildings() {
        return List.copyOf(buildings.values());
    }

    /** The yard: what a side builds with. */
    public BuildingType yard() {
        return buildings.values().stream().filter(BuildingType::yard).findFirst().orElseThrow();
    }

    /** The harvester: the first vehicle that harvests. */
    public UnitType harvester() {
        return units.values().stream().filter(UnitType::harvests).findFirst()
                .orElseThrow(() -> new DataException("units.txt: no vehicle harvests (capacity)"));
    }

    public Mission mission(int number) {
        Mission mission = missions.get(number);
        if (mission == null) throw new DataException("missions.txt: no mission " + number);
        return mission;
    }

    public int missions() {
        return missions.size();
    }

    public Plan plan() {
        return plan;
    }
}
