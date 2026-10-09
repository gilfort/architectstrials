package com.gilfort.architectstrials.gametest;

import java.util.function.Consumer;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.block.SpawnMarkerBlockEntity;
import com.gilfort.architectstrials.block.TrialSpawnerMarkerBlockEntity;
import com.gilfort.architectstrials.block.VaultMarkerBlockEntity;
import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.marker.SpawnMarkerClipboard;
import com.gilfort.architectstrials.menu.EquipmentListMenu;
import com.gilfort.architectstrials.menu.GhostSlots;
import com.gilfort.architectstrials.menu.MarkerSlot;
import com.gilfort.architectstrials.menu.SpawnMarkerMenu;
import com.gilfort.architectstrials.menu.VaultMarkerMenu;
import com.gilfort.architectstrials.registry.ModBlocks;
import com.gilfort.architectstrials.registry.ModDataComponents;
import com.gilfort.architectstrials.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * GameTests for US-39: ghost slots in the marker, equipment list, loot and vault menus, and the Spawn Marker Tool.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID)
public final class GhostSlotGameTests {

    /** Menu slot index of the first hotbar slot in the single-row spawn marker menu (7 marker + 27 inventory slots). */
    private static final int SPAWN_MENU_HOTBAR = SpawnMarkerBlockEntity.SIZE + 27;

    private GhostSlotGameTests() {
    }

    /**
     * Registers the test functions.
     *
     * @param event the registry event
     */
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            register(helper, "ghost_slots_marker_menu", GhostSlotGameTests::markerMenu);
            register(helper, "ghost_slots_list_and_vault", GhostSlotGameTests::listLootVault);
            register(helper, "spawn_marker_tool_copy_paste", GhostSlotGameTests::spawnMarkerTool);
        });
    }

    private static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> helper, String name,
            Consumer<GameTestHelper> test) {
        helper.register(ResourceKey.create(Registries.TEST_FUNCTION, ArchitectsTrials.id(name)), test);
    }

    /**
     * Spawn egg and equipment slots of a marker menu: clicks copy the cursor item without consuming it, empty-hand
     * clicks count up / down / clear, wrong items are ignored, shift click in the inventory copies, payload values
     * are clamped, and automation can neither insert nor extract.
     */
    private static void markerMenu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, ModBlocks.DIRECT_SPAWN_MARKER.get().defaultBlockState(), 3);
        SpawnMarkerBlockEntity marker = (SpawnMarkerBlockEntity) level.getBlockEntity(pos);
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        SpawnMarkerMenu menu = (SpawnMarkerMenu) marker.createMenu(1, player.getInventory(), player);
        int egg = SpawnMarkerBlockEntity.EGG_SLOT;
        int head = SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD);

        menu.setCarried(new ItemStack(Items.ZOMBIE_SPAWN_EGG, 8));
        menu.clicked(egg, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).getCount() == 8, "Left click did not copy 8 eggs: " + marker.egg(0));
        helper.assertTrue(menu.getCarried().getCount() == 8, "Cursor eggs were consumed");
        menu.clicked(egg, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).getCount() == 9, "Right click with eggs did not add one");
        menu.setCarried(new ItemStack(Items.SKELETON_SPAWN_EGG, 2));
        menu.clicked(egg, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).is(Items.SKELETON_SPAWN_EGG) && marker.egg(0).getCount() == 2, "Other egg did not replace");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(egg, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).is(Items.SKELETON_SPAWN_EGG), "A diamond was accepted as spawn egg");

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(egg, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).getCount() == 3, "Empty left click did not count up");
        menu.clicked(egg, 1, ContainerInput.PICKUP, player);
        menu.clicked(egg, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).getCount() == 1, "Empty right click did not count down");
        menu.clicked(egg, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.egg(0).isEmpty(), "Counting below 1 did not clear the slot");
        helper.assertTrue(menu.getCarried().isEmpty(), "A ghost item ended up in the cursor");

        menu.setCarried(new ItemStack(Items.IRON_HELMET, 1));
        menu.clicked(head, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.getItem(head).is(Items.IRON_HELMET) && !menu.getCarried().isEmpty(), "Helmet was not copied");
        menu.setCarried(new ItemStack(Items.DIAMOND_SWORD));
        menu.clicked(head, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.getItem(head).is(Items.IRON_HELMET), "A sword was accepted in the head slot");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(head, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(marker.getItem(head).isEmpty(), "Shift click did not clear the equipment slot");
        marker.setEquipmentList(head, EquipmentList.EMPTY.with(0, new ItemStack(Items.GOLDEN_HELMET), 500));
        helper.assertFalse(GhostSlots.set(menu, head, new ItemStack(Items.IRON_HELMET)), "Fixed item accepted in a slot with a list");
        marker.setEquipmentList(head, EquipmentList.EMPTY);

        player.getInventory().setItem(0, new ItemStack(Items.CREEPER_SPAWN_EGG, 5));
        menu.quickMoveStack(player, SPAWN_MENU_HOTBAR);
        helper.assertTrue(marker.egg(0).is(Items.CREEPER_SPAWN_EGG) && marker.egg(0).getCount() == 5, "Shift click did not copy the eggs");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 5, "Shift click consumed the inventory eggs");

        helper.assertTrue(GhostSlots.set(menu, egg, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 500)), "Payload was rejected");
        helper.assertTrue(marker.egg(0).getCount() == 64, "Payload count was not clamped: " + marker.egg(0).getCount());
        helper.assertFalse(GhostSlots.set(menu, head, new ItemStack(Items.DIAMOND_SWORD)), "Payload put a sword into the head slot");
        helper.assertFalse(GhostSlots.set(menu, SPAWN_MENU_HOTBAR, new ItemStack(Items.DIAMOND)), "Payload changed an inventory slot");

        helper.assertFalse(marker.canPlaceItem(egg, new ItemStack(Items.ZOMBIE_SPAWN_EGG)), "Automation may insert into the marker");
        helper.assertFalse(marker.canTakeItem(marker, egg, marker.egg(0)), "Automation may extract from the marker");
        TestPlayers.finish(helper, player);
    }

    /**
     * Equipment list and vault key slots are ghost slots too (the loot setup menu sends payloads on every change,
     * which mock players cannot receive; it shares the same slot class and click handling).
     */
    private static void listLootVault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);

        BlockPos markerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(markerPos, ModBlocks.DIRECT_SPAWN_MARKER.get().defaultBlockState(), 3);
        SpawnMarkerBlockEntity marker = (SpawnMarkerBlockEntity) level.getBlockEntity(markerPos);
        int head = SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD);
        EquipmentListMenu list = new EquipmentListMenu(1, player.getInventory(), marker, head);
        list.setCarried(new ItemStack(Items.GOLDEN_HELMET));
        list.clicked(0, 0, ContainerInput.PICKUP, player);
        EquipmentList entries = marker.equipmentList(head);
        helper.assertTrue(entries.at(0).map(entry -> entry.item().is(Items.GOLDEN_HELMET)).orElse(false), "List entry was not copied");
        helper.assertFalse(list.getCarried().isEmpty(), "List click consumed the helmet");
        list.setCarried(ItemStack.EMPTY);
        list.clicked(0, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(marker.equipmentList(head).isEmpty(), "Right click did not remove the list entry");

        BlockPos vaultPos = helper.absolutePos(new BlockPos(3, 2, 5));
        level.setBlock(vaultPos, ModBlocks.VAULT_MARKER.get().defaultBlockState(), 3);
        VaultMarkerBlockEntity vault = (VaultMarkerBlockEntity) level.getBlockEntity(vaultPos);
        VaultMarkerMenu vaultMenu = (VaultMarkerMenu) vault.createMenu(3, player.getInventory(), player);
        vaultMenu.setCarried(new ItemStack(Items.TRIAL_KEY, 2));
        vaultMenu.clicked(VaultMarkerBlockEntity.KEY_SLOT, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(vault.key().is(Items.TRIAL_KEY) && vault.key().getCount() == 2, "Vault key was not copied");
        helper.assertTrue(vaultMenu.getCarried().getCount() == 2, "Vault click consumed the keys");
        helper.assertFalse(vault.canTakeItem(vault, VaultMarkerBlockEntity.KEY_SLOT, vault.key()), "Automation may extract the key");
        TestPlayers.finish(helper, player);
    }

    /**
     * The tool copies the complete configuration (shift + right click) and pastes it onto markers of the same type
     * only, replacing everything there.
     */
    private static void spawnMarkerTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos target = helper.absolutePos(new BlockPos(5, 2, 3));
        BlockPos spawner = helper.absolutePos(new BlockPos(3, 2, 5));
        BlockPos trialSource = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos trialTarget = helper.absolutePos(new BlockPos(7, 2, 5));
        level.setBlock(source, ModBlocks.DIRECT_SPAWN_MARKER.get().defaultBlockState(), 3);
        level.setBlock(target, ModBlocks.DIRECT_SPAWN_MARKER.get().defaultBlockState(), 3);
        level.setBlock(spawner, ModBlocks.SPAWNER_MARKER.get().defaultBlockState(), 3);
        level.setBlock(trialSource, ModBlocks.TRIAL_SPAWNER_MARKER.get().defaultBlockState(), 3);
        level.setBlock(trialTarget, ModBlocks.TRIAL_SPAWNER_MARKER.get().defaultBlockState(), 3);

        SpawnMarkerBlockEntity from = (SpawnMarkerBlockEntity) level.getBlockEntity(source);
        from.setItem(SpawnMarkerBlockEntity.EGG_SLOT, new ItemStack(Items.ZOMBIE_SPAWN_EGG, 3));
        from.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.MAINHAND), new ItemStack(Items.IRON_SWORD));
        from.setEquipmentList(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD),
                EquipmentList.EMPTY.with(0, new ItemStack(Items.LEATHER_HELMET), 500));
        from.setRequired(true);
        SpawnMarkerBlockEntity to = (SpawnMarkerBlockEntity) level.getBlockEntity(target);
        to.setItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.FEET), new ItemStack(Items.GOLDEN_BOOTS));

        ServerPlayer player = TestPlayers.atStart(helper, GameType.CREATIVE);
        ItemStack tool = new ItemStack(ModItems.SPAWN_MARKER_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.setShiftKeyDown(true);
        tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(source), Direction.UP, source, false)));
        player.setShiftKeyDown(false);
        SpawnMarkerClipboard clipboard = player.getMainHandItem().get(ModDataComponents.SPAWN_MARKER_CLIPBOARD.get());
        helper.assertTrue(clipboard != null && clipboard.eggs().size() == 1 && clipboard.eggs().getFirst().getCount() == 3,
                "Tool did not copy the configuration: " + clipboard);

        helper.assertTrue(clipboard.fits(to), "Clipboard does not fit a marker of the same type");
        helper.assertFalse(clipboard.fits((SpawnMarkerBlockEntity) level.getBlockEntity(spawner)), "Clipboard fits a Spawner Marker");
        helper.assertFalse(clipboard.fits((TrialSpawnerMarkerBlockEntity) level.getBlockEntity(trialSource)), "Clipboard fits a Trial Spawner Marker");
        clipboard.pasteInto(to);
        helper.assertTrue(to.egg(0).is(Items.ZOMBIE_SPAWN_EGG) && to.egg(0).getCount() == 3, "Eggs were not pasted");
        helper.assertTrue(to.getItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.MAINHAND)).is(Items.IRON_SWORD), "Fixed equipment was not pasted");
        helper.assertTrue(to.getItem(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.FEET)).isEmpty(), "Old equipment of the target was kept");
        helper.assertFalse(to.equipmentList(SpawnMarkerBlockEntity.indexOf(EquipmentSlot.HEAD)).isEmpty(), "Random list was not pasted");
        helper.assertTrue(to.required(), "Required flag was not pasted");
        helper.assertTrue(from.egg(0).getCount() == 3, "Source marker changed");

        TrialSpawnerMarkerBlockEntity trial = (TrialSpawnerMarkerBlockEntity) level.getBlockEntity(trialSource);
        trial.setItem(MarkerSlot.ROW_SIZE * TrialSpawnerMarkerBlockEntity.OMINOUS_FIRST_ROW, new ItemStack(Items.HUSK_SPAWN_EGG, 4));
        trial.setSimultaneousMobs(5);
        trial.setOminousAllowed(true);
        TrialSpawnerMarkerBlockEntity trialTo = (TrialSpawnerMarkerBlockEntity) level.getBlockEntity(trialTarget);
        SpawnMarkerClipboard.copy(trial).pasteInto(trialTo);
        helper.assertTrue(trialTo.simultaneousMobs(false) == 5 && trialTo.ominousAllowed(), "Trial spawner settings were not pasted");
        helper.assertTrue(trialTo.getItem(MarkerSlot.ROW_SIZE * TrialSpawnerMarkerBlockEntity.OMINOUS_FIRST_ROW).is(Items.HUSK_SPAWN_EGG),
                "Ominous row was not pasted");
        TestPlayers.finish(helper, player);
    }
}
