package dev.mobarena.gametest;

import dev.mobarena.arena.*;
import dev.mobarena.core.Phase;
import dev.mobarena.debug.DebugDriver;
import dev.mobarena.result.ResultModule;
import dev.mobarena.team.TeamId;
import dev.mobarena.wave.WaveKeys;
import dev.mobarena.world.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.*;

/** /ma stop must dispose the complete game from every phase. */
public class FullGameStopTest {
    @GameTest(environment = "mobarena:full_stop_lobby", maxTicks = 500)
    public void stopInLobby(GameTestHelper h) { stop(h, Phase.LOBBY); }
    @GameTest(environment = "mobarena:full_stop_prep", maxTicks = 500)
    public void stopInPrep(GameTestHelper h) { stop(h, Phase.PREP); }
    @GameTest(environment = "mobarena:full_stop_config", maxTicks = 500)
    public void stopInWaveConfig(GameTestHelper h) { stop(h, Phase.WAVE_CONFIG); }
    @GameTest(environment = "mobarena:full_stop_arena", maxTicks = 600)
    public void stopInArena(GameTestHelper h) { stop(h, Phase.ARENA); }
    @GameTest(environment = "mobarena:full_stop_sudden", maxTicks = 600)
    public void stopInSuddenDeath(GameTestHelper h) { stop(h, Phase.SUDDEN_DEATH); }
    @GameTest(environment = "mobarena:full_stop_end", maxTicks = 600)
    public void stopInEnd(GameTestHelper h) { stop(h, Phase.END); }

    private static void stop(GameTestHelper h, Phase phase) {
        var f = new FullGameFixture(h, phase != Phase.LOBBY);
        try {
            if (phase == Phase.LOBBY) {
                try {
                    int result = f.server.getCommands().getDispatcher().execute("ma stop", f.server.createCommandSourceStack());
                    h.assertTrue(result == 0, "Stop in the lobby reports no running game");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
                cleanLobby(h, f); f.state(Phase.LOBBY); f.close(); h.succeed(); return;
            }
            f.red.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 7));
            if (phase == Phase.PREP) {
                f.command("ma backpack", f.red);
                stopAndCheck(h, f); return;
            }
            if (phase == Phase.WAVE_CONFIG) {
                f.waveConfig(); f.command("ma waves", f.red); f.command("ma waves", f.redMate);
                h.assertTrue(f.red.containerMenu != f.red.inventoryMenu && f.redMate.containerMenu != f.redMate.inventoryMenu,
                        "Both wave menus are open before stop");
                stopAndCheck(h, f); return;
            }
            f.arena();
            var sequence = h.startSequence().thenWaitUntil(f::arenaReady)
                    .thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).waveMobs().size() == 1
                            && f.battle(TeamId.BLUE).waveMobs().size() == 1, "Both arenas have live wave mobs"));
            if (phase == Phase.SUDDEN_DEATH) {
                sequence.thenExecute(f.check(() -> {
                    // Real path: enterSuddenDeath -> ResultModule sudden death starter.
                    // TODO(T8b part 2): remove this test starter when T8b part 2 installs the real one.
                    var previous = ResultModule.suddenDeathStarter();
                    ResultModule.setSuddenDeathStarter((ctx, hooks) -> ArenaPhaseListener.startSuddenDeath(ctx, BattleListener.resultHooks(ctx)));
                    try { f.game.enterSuddenDeath(); }
                    finally { ResultModule.setSuddenDeathStarter(previous); }
                    h.assertTrue(f.game.phase() == Phase.SUDDEN_DEATH, "enterSuddenDeath reaches SUDDEN_DEATH");
                })).thenWaitUntil(() -> h.assertTrue(f.battle(TeamId.RED).waveMobs().size() == 3
                        && f.battle(TeamId.BLUE).waveMobs().size() == 3, "Sudden death has live combined waves"));
            } else if (phase == Phase.END) sequence.thenExecute(f.check(f.game::finish));
            sequence.thenExecute(f.check(() -> {
                h.assertTrue(f.game.phase() == phase, "Stop starts from " + phase);
                if (phase != Phase.END) f.command("ma backpack", f.red);
                stopAndCheck(h, f);
            }));
        } catch (RuntimeException | Error e) { f.close(); throw e; }
    }

    private static void stopAndCheck(GameTestHelper h, FullGameFixture f) {
        var worlds = f.ctx.get(TeamWorlds.KEY);
        List<ServerLevel> levels = worlds.values().stream().flatMap(TeamWorlds::handles).map(handle -> handle.asLevel()).toList();
        List<ResourceKey<Level>> keys = levels.stream().map(ServerLevel::dimension).toList();
        List<? extends Entity> waveMobs = levels.stream().flatMap(level -> waveEntities(level).stream()).toList();
        f.admin("ma stop");
        cleanLobby(h, f);
        h.startSequence().thenWaitUntil(() -> {
            for (var key : keys) {
                h.assertTrue(f.server.getLevel(key) == null, "Game level is removed: " + key.identifier());
                var path = f.server.getWorldPath(LevelResource.ROOT).resolve("dimensions")
                        .resolve(key.identifier().getNamespace()).resolve(key.identifier().getPath());
                h.assertTrue(!Files.exists(path), "Game level directory is deleted: " + path);
            }
        }).thenExecute(f.check(() -> {
            cleanLobby(h, f);
            h.assertTrue(waveMobs.stream().allMatch(Entity::isRemoved), "Every captured wave mob is removed");
            for (var level : f.server.getAllLevels()) h.assertTrue(waveEntities(level).isEmpty(), "No wave mobs remain in an open level");
            var hook = dev.mobarena.randomizer.RandomizerModule.hook();
            h.assertTrue(hook.mapFor("RED").isEmpty() && hook.mapFor("BLUE").isEmpty(), "Stop clears randomizer maps");
            var state = DebugDriver.state(f.server);
            h.assertTrue(state.get("phase").getAsString().equals("LOBBY") && state.get("timer").isJsonNull(), "State reports an idle lobby");
            var open = state.getAsJsonArray("openLevels");
            for (var key : keys) h.assertTrue(!open.asList().contains(new com.google.gson.JsonPrimitive(key.identifier().toString())), "State excludes deleted levels");
        })).thenExecute(f::close).thenSucceed();
    }
    private static List<? extends Entity> waveEntities(ServerLevel level) {
        return level.getEntities(EntityTypeTest.forClass(Entity.class),
                e -> e.entityTags().stream().anyMatch(tag -> tag.startsWith("mobarena.wave.")));
    }
    private static void cleanLobby(GameTestHelper h, FullGameFixture f) {
        h.assertTrue(f.game.phase() == Phase.LOBBY && f.game.context() == null, "Stop clears game context");
        for (var p : f.players) {
            h.assertTrue(p.level() == f.server.overworld() && p.gameMode() == GameType.ADVENTURE, "Game players return to the lobby");
            h.assertTrue(p.getInventory().isEmpty(), "Lobby inventory is empty");
            h.assertTrue(p.containerMenu == p.inventoryMenu && p.containerMenu.getCarried().isEmpty(), "No GUI or cursor item remains");
            h.assertTrue(p.getAbilities().invulnerable, "Lobby players are protected");
        }
    }
}
