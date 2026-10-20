package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;
import eu.amadeos.gameengine.audio.Tune;

/**
 * The music of Sands, written in MML: a slow, dark desert tune in E minor with a drone below it, looped
 * while a mission runs, and short tunes for a mission won and lost.
 */
final class Music {
    /** The desert: a tune in the Phrygian mode over a bass that keeps to E and B. */
    final Sound desert = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.04, "t84 o4 l8 e4 f e d+ e2 | g4 f e f e4 r4 | b4 a g f e4 d e | f2 e2"
                    + " | e4 f g a4 b4 | >c4< b a g f4 e4 | d e f e d c< b >c | e1"),
            Tune.voice(Wave.TRIANGLE, 0.17, "t84 o2 l4 e e b e | e e b e | e e b b | f f e e"
                    + " | e e b e | a a e e | d d c c | e b e2"));

    /** A mission won. */
    final Sound won = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.07, "t132 o4 l8 e g b >e4 < b >e g4 e2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t132 o2 l8 e b >e4 < e4 b4 e2"));

    /** A mission lost. */
    final Sound lost = Sound.mix(
            Tune.voice(Wave.SQUARE, 0.06, "t72 o4 l4 b g f d+ e2"),
            Tune.voice(Wave.TRIANGLE, 0.2, "t72 o2 l2 e b e1"));
}
