package eu.amadeos.games.sands.domain;

import eu.amadeos.gameengine.data.TextData;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {
    private static List<TextData.Line> file(String name) {
        return TextData.load(Rules.class, Rules.FOLDER + name);
    }

    @Test
    void theDataFilesDescribeTheVehiclesBuildingsMissionsAndThePlan() {
        Rules rules = Rules.load();
        assertEquals(List.of("trike", "tank", "launcher", "harvester"), rules.units().stream().map(UnitType::id).toList());
        assertEquals("yard", rules.yard().id());
        assertFalse(rules.yard().buildable(), "the map places the yard; nobody builds it");
        assertTrue(rules.building("factory").factory());
        assertTrue(rules.building("outpost").radar());
        assertEquals(100, rules.building("windtrap").power());
        assertTrue(rules.building("refinery").power() < 0, "every other building uses power");
        assertEquals(Weapon.Kind.ROCKET, rules.unit("launcher").weapon().kind());
        assertTrue(rules.unit("launcher").weapon().splash() > 0);
        assertNull(rules.harvester().weapon(), "the harvester is unarmed");
        assertEquals(3, rules.missions());
        assertFalse(rules.mission(1).worm());
        assertTrue(rules.mission(2).worm());
        assertEquals("windtrap", rules.plan().build().getFirst().id());
    }

    @Test
    void aMistakeInADataFileNamesItsLine() {
        List<TextData.Line> units = TextData.parse("units.txt", new StringReader("""
                unit trike
                  health 100
                  armour 0.1
                  speed 3.4
                  sight 5
                  size 0.28
                  cost 150
                  seconds 8
                  wepaon gun
                """));
        assertEquals("units.txt:9: \"wepaon\" is not a property of a unit", assertThrows(TextData.DataException.class,
                () -> Rules.read(units, file("buildings.txt"), file("missions.txt"), file("commander.txt"))).getMessage());

        List<TextData.Line> plan = TextData.parse("commander.txt", new StringReader("""
                plan computer
                  build windtrap palace
                  mix tank
                  harvesters 1
                """));
        assertEquals("commander.txt:2: \"palace\" is not a building to build", assertThrows(TextData.DataException.class,
                () -> Rules.read(file("units.txt"), file("buildings.txt"), file("missions.txt"), plan)).getMessage());
    }
}
