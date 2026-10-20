# amade-sands-game

**amade-sands-game** is the real-time strategy game of the [amadeOS](https://github.com/datazuul/amadeos) desktop: two houses on a desert planet, each with a construction yard on a plateau of rock. Harvesters gather the crystals that lie in the sand, refineries turn it into credits, and the credits pay for wind turbines, factories, turrets and an army of trikes, tanks and rocket launchers. The computer builds its own base, harvests and attacks in growing waves; you win when its last building falls. A sand burrower hunts on the open sand of the later missions. Three missions on maps larger than the screen, made in the [Tiled](https://www.mapeditor.org/) editor; the vehicles, buildings, missions and the computer's plan are text files. A battle can be saved and loaded. Built on [amade-game-engine](https://github.com/datazuul/amade-game-engine); runs on other operating systems as well.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Playing

- A click, **Space** or **Enter** leads from the title to the choice of a mission. Click one, or choose with **left** and **right** and play with **Space** or **Enter**. A mission opens when the one before it is won; the fastest win of each is kept.
- **Left button**: click a vehicle to choose it, or draw a box around several. **Shift** adds to the choice. A click on one of your buildings, or on an enemy vehicle or building in sight, shows it in the panel with its health. **Ctrl** and a number from **1** to **9** keeps the chosen vehicles as a group; the number alone chooses them again.
- **Right button** on the map: the chosen vehicles attack an enemy under the mouse, harvesters go to the crystal or home to a refinery, everything else drives there. With **Ctrl** they fight their way there, attacking what they meet. **S** or *Stop* stops them.
- **The panel** on the right shows your credits, the minimap and the power. Click the minimap to look there, drag on it to scroll, right-click it to send the chosen vehicles. *Buildings* and *Vehicles* switch the build list. A click on a building starts it; when it is ready, click it again and then the place on the map where it should stand: on rock, near your base, where nothing is in the way (green yes, red no). The factory takes up to five vehicles in a row. The right button on the list takes back an order and returns its credits. *Sell* sells the chosen building for half its price.
- **Wind turbines** make the power every other building uses. With too little power, the yard and the factory work at half speed and the outpost's minimap goes dark. A **refinery** comes with a harvester, brought by an airlifter; **silos** store more crystal. Crystal beyond what you can store is lost.
- The fog of war lifts where your vehicles and buildings see. Ground you have seen stays dim, with the enemy's buildings as you last saw them.
- **F3** shows the strength of your army as the computer sees it: where it is red, the computer expects resistance and attacks elsewhere.
- **F5** saves the battle, **F9** loads it, here and on the choice of a mission.
- **Arrow keys**, dragging with the middle mouse button, or the mouse at the edge of the screen move the view.
- **P** or *Menu* pauses, and so does leaving the window; there you can save, load or leave the mission. **Esc** takes back the choice, or pauses when nothing is chosen.
- **F11** switches to full screen and back. *Game → Graphics* chooses how the battlefield reaches the screen: **Direct** (at the screen's full size) or **Pixels** (at 640 × 400, enlarged by whole factors). **F12** shows how long each frame takes.
- Sound effects and music can be switched off under *Game → Sound*.

Texts in English, Deutsch, Français, Español and Italiano.

## Files

| What | Where |
|---|---|
| Settings (graphics, sound) | `~/.config/amadeos/sands/sands.properties` |
| Progress (open missions, fastest wins) | `~/.local/share/amadeos/games/sands/progress.save` |
| The saved battle (F5) | `~/.local/share/amadeos/games/sands/battle.save` |

Both save files are written to a new file that then replaces the old one, with the old one kept as `.bak` and a checksum in the last line, so a crash while saving or a damaged file loses nothing. The saved battle holds the whole game: every vehicle and building with its orders, the crystal, both fogs of war, the factories' queues, the computer's plans and the random numbers, so a loaded battle goes on exactly as it would have.

## Making missions

The maps are `src/main/resources/eu/amadeos/games/sands/maps/map1.tmx` to `map3.tmx`, made for Tiled 1.10 or later, with the tileset `tiles.tsx` and its picture `tiles.png`. Open them in Tiled, change them, save; the game reads them as they are.

- A map is orthogonal, not infinite, with tiles of 16 × 16 pixels.
- The tile layer **ground** says what each tile is; the tileset gives each tile a class: `sand` (also a tile without a class), `dune` (slower), `rock` (where buildings stand; the burrower cannot reach it), `cliff` (blocks everything), `crystal` and `thick` (sand with crystal, a little or a lot). The game draws the ground itself, so `tiles.png` only shows Tiled which tile is which.
- The object layer **things** holds the buildings and vehicles at the start, each a rectangle whose class is the name of a building of `buildings.txt` or a vehicle of `units.txt`, with the property `side` set to `blue` (the player) or `red` (the computer). Each side needs at least one building, normally a `yard`, and buildings must stand on rock.
- A map with tiles of another size, an unknown class, a building off the rock or outside the map, or a side without a building is rejected with its file and the object.

What the houses build and how the computer plays is in `src/main/resources/eu/amadeos/games/sands/data/`, explained at the top of each file:

- `units.txt`: each vehicle's health, armour, speed, sight, size, cost, time, weapon and what it needs.
- `buildings.txt`: each building's size, health, cost, time, power, sight, storage, what it needs, and whether it is the yard, the factory, the radar, a refinery with a dock or a turret with a weapon.
- `missions.txt`: each mission's map, the credits of both houses, the burrower, and when and how hard the computer attacks.
- `commander.txt`: the order in which the computer builds its base and the mix of its army.

Change a number, start the game again, play. A mistake is reported with its file and line. `GameTest` lets the computer play mission 1 for eight minutes, so a plan that never builds a base or never attacks fails the build.

## Building

```bash
./gradlew test          # the window test needs a display (xvfb-run on servers)
./gradlew run
./gradlew deb           # build/distributions/amade-sands-game_<version>_all.deb
./gradlew installToAmadeOs   # copies the package to ../amadeos/config/packages.chroot/
```

All pictures are drawn by the game from vector shapes (`Art`) when the window opens, at the size of the screen: buildings, vehicles in 32 directions, the burrower, wrecks and explosions. The ground is drawn in squares of 8 × 8 tiles on background threads, with the crystal mixed in again when it is harvested. `./gradlew art` draws `maps/tiles.png` and the icon from the same shapes, `./gradlew screenshots -Pshots=<folder>` the handbook's screenshots, and `./gradlew benchmark` measures the frame times of every mission.

With `../amade-game-engine` (and `../amade-commons`) checked out next to this repository, Gradle builds those checkouts instead of the JitPack versions.

## Structure

- `domain`: the rules without user interface or time of their own. `Game` (a battle: vehicles and their orders, group moves, harvesting, building and placing, power, production, shots, the burrower, airlifters, fog of war, winning), `House` (a side's credits, storage, power, yard and factory queues, fog and what it has seen), `Commander` (the computer: its base plan, harvesters, defence and attacks guided by an influence map), `Archive` (the whole game to and from a save file), `Level` (reads a Tiled map), `Rules` (reads the four data files), `UnitType`, `BuildingType`, `Weapon`, `Mission`, `Plan`, `Unit`, `Building`, `Ghost` (an enemy building as last seen), `Shot`, `Burrower`, `Airlifter`, `Ground`, `Side`, `Task`, `Event`, `Field` (sizes and numbers).
- `repository`: `SandsSettings` (graphics, sound), `Progress` (open missions and fastest wins), `SaveSlot` (the saved battle).
- `ui.game`: the running game on the loop thread. `SandsGame` (loop, mouse and keys, scenes: title, choice, battle, pause, result; the camera, selection and orders), `SandsPainter` (the battlefield as the camera sees it, in layers), `Terrain` (the ground in chunks, drawn in the background), `Art` (all drawings), `Pictures` (the sprites made of them), `Hud` (the panel, the cards and texts), `Effects` (events become explosions, tracks, smoke, sounds and music), `SoundBank`, `Music` (the tunes, in MML), `Arena` (what the presenter may ask of the game).
- `ui.main`: Model-View-Presenter for everything around the battle: `SandsPresenter`, `SandsView`, `SandsWindow`.

From the engine: `Selection` and `BoxSelect` (choosing units), `GroupMove` and `PathFinder` (group pathfinding), `FogOfWar` and `FogPainter`, `Minimap`, `Production` and `Credits` (queues and money), `InfluenceMap` (the computer's view), `Snapshot` (saving a whole game), `Catalog`, `TiledMap`, `Camera`, `Rotations` and `Sprite`, `Particles`, `SaveFile`, `GameLoop`, `GameCanvas`, `Sound`, `Tune` and `Mixer`, `Keyboard`, `Mouse` and `Controls`, `SceneStack`, `FullScreen`.

Comments of the form `// --8<-- [start:name]` mark the excerpts the [Game Developer's Handbook](https://datazuul.github.io/amade-game-engine/) quotes; chapter 31 is about this game.

## Licence

[EUPL-1.2](LICENSE)
