package eu.amadeos.games.sands.ui.game;

import eu.amadeos.gameengine.audio.Sound;
import eu.amadeos.gameengine.audio.Sound.Wave;

/** The sound effects of Sands, made once at start from tones: no recordings. */
final class SoundBank {
    /** A trike's machine gun: three quick knocks. */
    final Sound gun = Sound.tone(Wave.NOISE, 0, 0, 0.025, 0.06)
            .then(Sound.tone(Wave.NOISE, 0, 0, 0.025, 0.05))
            .then(Sound.tone(Wave.NOISE, 0, 0, 0.03, 0.04));
    /** A tank's or a turret's gun: a deep thump under a burst of noise. */
    final Sound cannon = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.18, 0.12),
            Sound.tone(Wave.TRIANGLE, 140, 50, 0.22, 0.3));
    /** A rocket leaves its tube: a hiss that rises. */
    final Sound rocket = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.35, 0.07),
            Sound.tone(Wave.SQUARE, 300, 900, 0.3, 0.025));
    final Sound hit = Sound.tone(Wave.NOISE, 0, 0, 0.04, 0.05);
    /** A shell or a rocket bursts. */
    final Sound burst = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.3, 0.13),
            Sound.tone(Wave.TRIANGLE, 100, 35, 0.3, 0.28));
    /** A vehicle blows up. */
    final Sound boom = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.6, 0.2),
            Sound.tone(Wave.TRIANGLE, 80, 25, 0.6, 0.4));
    /** A building falls: a long rumble. */
    final Sound collapse = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 1.2, 0.18),
            Sound.tone(Wave.TRIANGLE, 60, 22, 1.2, 0.4));
    /** Steel set on concrete: a building stands. */
    final Sound placed = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.08, 0.12),
            Sound.tone(Wave.TRIANGLE, 220, 140, 0.12, 0.3))
            .then(Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 0.06, 0.1), Sound.tone(Wave.TRIANGLE, 180, 120, 0.1, 0.25)));
    /** Something is ready: two rising notes. */
    final Sound ready = Sound.tone(Wave.SQUARE, 659, 659, 0.08, 0.05)
            .then(Sound.tone(Wave.SQUARE, 988, 988, 0.16, 0.05));
    /** Credits come in: a quick run of high notes. */
    final Sound credits = Sound.tone(Wave.SQUARE, 1175, 1175, 0.04, 0.04)
            .then(Sound.tone(Wave.SQUARE, 1568, 1568, 0.08, 0.04));
    /** The base is attacked: a siren, up and down twice. */
    final Sound alarm = Sound.tone(Wave.SQUARE, 440, 660, 0.25, 0.05).then(Sound.tone(Wave.SQUARE, 660, 440, 0.25, 0.05))
            .then(Sound.tone(Wave.SQUARE, 440, 660, 0.25, 0.05)).then(Sound.tone(Wave.SQUARE, 660, 440, 0.25, 0.05));
    /** The worm comes up: a deep rumble under hissing sand. */
    final Sound worm = Sound.mix(Sound.tone(Wave.NOISE, 0, 0, 1.4, 0.1),
            Sound.tone(Wave.TRIANGLE, 45, 30, 1.4, 0.45));
    /** A vehicle answers an order. */
    final Sound yes = Sound.tone(Wave.SQUARE, 523, 523, 0.04, 0.04).then(Sound.tone(Wave.SQUARE, 784, 784, 0.06, 0.04));
    /** Not possible: a low buzz. */
    final Sound no = Sound.tone(Wave.SQUARE, 150, 140, 0.15, 0.06);
    /** A button, or a menu choice moves. */
    final Sound tick = Sound.tone(Wave.SQUARE, 880, 880, 0.03, 0.04);
}
