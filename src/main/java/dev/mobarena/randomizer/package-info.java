/**
 * T3 item randomization.
 *
 * <p>Call {@code RandomizerModule.init()} once during mod initialization. At game start, call
 * {@code RandomizerModule.hook().startGame(seed, teamKeys, lookup, randomizerEnabled)}.
 * The lookup must return the same team key for every world owned by that team, including its
 * Nether. Return an empty optional for lobby and other worlds. Call {@code hook().clear()} at
 * game end. Maps are immutable and available through {@code hook().mapFor(teamKey)}.
 *
 * <p>Loot generation uses the Fabric event. Equipment generation stays unchanged; equipment
 * drops are mapped by the T3 mob mixin. Player inventory drops and merchant output are untouched.
 * All mutable state must be accessed on the server thread.
 */
package dev.mobarena.randomizer;
