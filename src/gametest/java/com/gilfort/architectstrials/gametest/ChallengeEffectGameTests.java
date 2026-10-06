package com.gilfort.architectstrials.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.instance.ChallengeClock;
import com.gilfort.architectstrials.instance.ChallengeInstance;
import com.gilfort.architectstrials.instance.InstanceCreation;
import com.gilfort.architectstrials.instance.InstanceManager;
import com.gilfort.architectstrials.registry.ModAttachments;
import com.gilfort.architectstrials.scroll.ScrollEffect;
import com.gilfort.architectstrials.scroll.ScrollEffects;
import com.gilfort.architectstrials.scroll.ScrollOptions;
import com.gilfort.architectstrials.structure.ChallengeStructures;
import com.gilfort.architectstrials.theme.ChallengeThemes;
import com.gilfort.architectstrials.travel.ChallengeTravel;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-36 (challenge-defined player effects and attributes). {@code minecraft:the_nether} tier 7 is the
 * spawn platform with night vision, speed II, an effect of a missing mod and a movement speed modifier.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class ChallengeEffectGameTests {

    private static final int TIER = 7;

    private ChallengeEffectGameTests() {
    }

    /**
     * Registers the test functions referenced by the test instance JSON files.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "challenge_effects_entry_and_leave", ChallengeEffectGameTests::entryAndLeave);
            register(helper, "challenge_effects_behind_scroll", ChallengeEffectGameTests::behindScroll);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Challenge effects last the remaining instance time, the attribute modifier is added, an unknown effect is
     * skipped; a removed effect stays removed; a late joiner gets everything too; leaving removes it all and gives
     * the player's own effect back.
     */
    private static void entryAndLeave(GameTestHelper helper) {
        helper.assertTrue(ChallengeStructures.get(ArchitectsTrials.id("the_nether/tier_7/effects_platform")).orElseThrow().playerEffects().size() == 2,
                "Unknown challenge effect was not skipped (or a valid one was dropped)");
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(nether, ScrollEffects.NONE);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300));
        double baseSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

        helper.assertTrue(InstanceManager.join(player, nether, instance), "Player could not join");
        long remaining = instance.deadline() - ChallengeClock.now(nether.getServer());
        MobEffectInstance nightVision = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(nightVision != null && Math.abs(nightVision.getDuration() - remaining) <= 20,
                "Permanent challenge effect does not last the remaining instance time: " + nightVision);
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 1, "Challenge effect speed II missing: " + speed);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - (baseSpeed - 0.02)) < 1.0E-6,
                "Challenge attribute modifier was not applied");

        player.removeEffect(MobEffects.SPEED);
        helper.assertFalse(player.hasEffect(MobEffects.SPEED), "Removed challenge effect came back");

        ServerPlayer lateJoiner = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(lateJoiner, nether, instance), "Late joiner could not join");
        helper.assertTrue(lateJoiner.hasEffect(MobEffects.NIGHT_VISION) && lateJoiner.hasEffect(MobEffects.SPEED),
                "Late joiner did not get the challenge effects");

        helper.assertTrue(ChallengeTravel.returnToEntryPoint(player), "Player could not return");
        MobEffectInstance own = player.getEffect(MobEffects.NIGHT_VISION);
        helper.assertTrue(own != null && own.getDuration() == 300, "Own effect was not given back unchanged: " + own);
        helper.assertFalse(player.hasEffect(MobEffects.SPEED), "Challenge effect was not removed on leaving");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 1.0E-6,
                "Challenge attribute modifier was not removed on leaving");
        helper.assertTrue(player.getData(ModAttachments.PARKED_EFFECTS).isEmpty(), "Bookkeeping was not cleared");

        ChallengeTravel.returnToEntryPoint(lateJoiner);
        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, lateJoiner);
        TestPlayers.finish(helper, player);
    }

    /**
     * A scroll effect of the same type lies on top; the challenge effect waits behind it and takes over after it.
     */
    private static void behindScroll(GameTestHelper helper) {
        ServerLevel nether = TestPlayers.challengeLevel(helper);
        ChallengeInstance instance = create(nether, new ScrollEffects(List.of(new ScrollEffect(ScrollEffect.Target.PLAYER, MobEffects.SPEED, 100, 0))));
        ServerPlayer player = TestPlayers.atStart(helper, GameType.SURVIVAL);
        helper.assertTrue(InstanceManager.join(player, nether, instance), "Player could not join");

        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 0 && speed.getDuration() == 100, "Scroll effect is not on top: " + speed);
        CompoundTag tag = (CompoundTag) MobEffectInstance.CODEC.encodeStart(NbtOps.INSTANCE, speed).getOrThrow();
        CompoundTag hidden = tag.getCompoundOrEmpty("hidden_effect");
        helper.assertTrue(hidden.getIntOr("amplifier", -1) == 1, "Challenge effect is not waiting behind the scroll effect: " + tag);

        ChallengeTravel.returnToEntryPoint(player);
        InstanceManager.close(nether, instance.id());
        TestPlayers.finish(helper, player);
    }

    private static ChallengeInstance create(ServerLevel nether, ScrollEffects effects) {
        InstanceCreation result = InstanceManager.create(nether, ChallengeThemes.get(Level.NETHER.identifier()).orElseThrow(), TIER,
                nether.getRandom(), InstanceManager.defaultTimeLimitTicks(), ScrollOptions.DEFAULT, effects);
        if (result instanceof InstanceCreation.Success(ChallengeInstance instance)) {
            return instance;
        }
        throw new IllegalStateException("Instance creation failed: " + ((InstanceCreation.Failure) result).reason().getString());
    }
}
