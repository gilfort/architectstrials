package com.gilfort.architectstrials.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.registry.ModMenuTypes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Menu of the loot tool: edits the {@link LootSetup} of one loot source, one page at a time (groups 1–5 and the
 * consolation list). The nine item slots show the fixed items of the current page; loot table entries, chances
 * and roll ranges are synchronized with {@link LootNetwork payloads}. Every change is stored in the loot source
 * immediately.
 */
public class LootSetupMenu extends AbstractContainerMenu {

    /** Button id of the first page (pages 0–5, 5 = consolation). */
    public static final int BUTTON_PAGE = 0;

    /** Button id that switches between the normal and the ominous setup (trial spawner markers). */
    public static final int BUTTON_VARIANT = 10;

    /** X position of the entry grid. */
    public static final int GRID_X = 8;

    /** Y position of the entry grid. */
    public static final int GRID_Y = 46;

    /** Width of an entry cell. */
    public static final int CELL_WIDTH = 82;

    /** Height of an entry cell. */
    public static final int CELL_HEIGHT = 42;

    /** X position of the player inventory. */
    public static final int INVENTORY_X = 48;

    /** Y position of the player inventory. */
    public static final int INVENTORY_Y = 186;

    /** Chance of a new entry: 10 %. */
    private static final int DEFAULT_CHANCE = 100;

    private final @Nullable BlockEntity target;
    private final @Nullable ServerPlayer player;
    private final Container items;
    private LootSetup setup = LootSetup.EMPTY;
    private int page;
    private boolean ominous;
    private boolean hasOminousVariant;
    private List<Identifier> lootTables = List.of();

    /**
     * Creates the client-side menu.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     */
    public LootSetupMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainer(LootGroup.MAX_ENTRIES));
    }

    /**
     * Creates the server-side menu for a loot source.
     *
     * @param containerId the container id
     * @param inventory   the player inventory
     * @param target      the loot source
     */
    public LootSetupMenu(int containerId, Inventory inventory, BlockEntity target) {
        this(containerId, inventory, target, null);
    }

    private LootSetupMenu(int containerId, Inventory inventory, @Nullable BlockEntity target, @Nullable Container clientItems) {
        super(ModMenuTypes.LOOT_SETUP.get(), containerId);
        this.target = target;
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.items = clientItems != null ? clientItems : new PageItems();
        if (target != null) {
            this.setup = LootSetups.get(target, false);
            this.hasOminousVariant = target instanceof LootSetupHolder holder && holder.hasOminousVariant();
        }
        for (int position = 0; position < LootGroup.MAX_ENTRIES; position++) {
            int x = GRID_X + (position % 3) * CELL_WIDTH + 1;
            int y = GRID_Y + (position / 3) * CELL_HEIGHT + 1;
            this.addSlot(new Slot(this.items, position, x, y) {
                @Override
                public int getMaxStackSize() {
                    return 99;
                }
            });
        }
        this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    /** @return the setup currently shown (synchronized to the client) */
    public LootSetup setup() {
        return this.setup;
    }

    /** @return the current page (0–4 groups, 5 consolation) */
    public int page() {
        return this.page;
    }

    /** @return {@code true} while the ominous setup is edited */
    public boolean ominous() {
        return this.ominous;
    }

    /** @return {@code true} if the loot source has a separate ominous setup */
    public boolean hasOminousVariant() {
        return this.hasOminousVariant;
    }

    /** @return all loot table ids known to the server (for the picker) */
    public List<Identifier> lootTables() {
        return this.lootTables;
    }

    /** @return {@code true} if the current page is the consolation list */
    public boolean consolationPage() {
        return this.page == LootSetup.MAX_GROUPS;
    }

    /**
     * Sends the current state and, optionally, the loot table list to the client.
     *
     * @param withTables {@code true} to include the loot table ids (on opening)
     */
    public void sync(boolean withTables) {
        if (this.player == null) {
            return;
        }
        if (withTables) {
            List<Identifier> ids = new ArrayList<>();
            this.player.level().getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                    .map(ResourceKey::identifier).filter(id -> !id.equals(LootSetups.PLACEHOLDER.identifier())).sorted().forEach(ids::add);
            PacketDistributor.sendToPlayer(this.player, new LootNetwork.TableList(this.containerId, ids));
        }
        PacketDistributor.sendToPlayer(this.player, new LootNetwork.Sync(this.containerId, this.setup, this.page, this.ominous,
                this.hasOminousVariant));
        this.broadcastChanges();
    }

    /**
     * Applies a state received from the server (client side).
     *
     * @param newSetup          the setup
     * @param newPage           the page
     * @param newOminous        whether the ominous setup is shown
     * @param ominousVariant    whether the source has an ominous setup
     */
    void applySync(LootSetup newSetup, int newPage, boolean newOminous, boolean ominousVariant) {
        this.setup = newSetup;
        this.page = newPage;
        this.ominous = newOminous;
        this.hasOminousVariant = ominousVariant;
    }

    /**
     * Stores the loot table list received from the server (client side).
     *
     * @param ids the loot table ids
     */
    void applyTables(List<Identifier> ids) {
        this.lootTables = List.copyOf(ids);
    }

    private void store(LootSetup updated) {
        this.setup = updated;
        if (this.target != null) {
            LootSetups.set(this.target, this.ominous, updated);
        }
        this.sync(false);
    }

    private LootGroup currentGroup() {
        return this.setup.page(this.page);
    }

    private void storeGroup(LootGroup group) {
        this.store(this.setup.withPage(this.page, group));
    }

    /**
     * Sets chance and roll range of an entry of the current page (server side).
     *
     * @param position the position
     * @param chance   the chance in tenths of a percent
     * @param min      the minimum rolls
     * @param max      the maximum rolls
     */
    void setValues(int position, int chance, int min, int max) {
        this.storeGroup(this.currentGroup().update(position, entry -> entry.withChance(chance).withRolls(min, max), !this.consolationPage()));
    }

    /**
     * Sets or removes the loot table of an entry of the current page (server side). A fixed item at that position
     * is kept; its entry becomes a table entry only if no item is set.
     *
     * @param position the position
     * @param table    the loot table, or empty to remove the table entry
     */
    void setTable(int position, Optional<ResourceKey<LootTable>> table) {
        LootGroup group = this.currentGroup();
        Optional<LootEntry> existing = group.at(position);
        if (existing.isPresent() && !existing.get().item().isEmpty()) {
            return;
        }
        if (table.isEmpty()) {
            this.storeGroup(group.with(new LootEntry(position, Optional.empty(), ItemStack.EMPTY, 0, 1, 1), true));
            return;
        }
        LootEntry entry = existing.map(e -> new LootEntry(position, table, ItemStack.EMPTY, e.chance(), e.rollsMin(), e.rollsMax()))
                .orElseGet(() -> LootEntry.ofTable(position, table.get(), DEFAULT_CHANCE));
        this.storeGroup(group.with(entry, !this.consolationPage()));
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (id >= BUTTON_PAGE && id <= BUTTON_PAGE + LootSetup.MAX_GROUPS) {
            this.page = id - BUTTON_PAGE;
            this.sync(false);
            return true;
        }
        if (id == BUTTON_VARIANT && this.hasOminousVariant && this.target != null) {
            this.ominous = !this.ominous;
            this.setup = LootSetups.get(this.target, this.ominous);
            this.sync(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player clicker) {
        return this.target == null || (!this.target.isRemoved()
                && clicker.distanceToSqr(Vec3.atCenterOf(this.target.getBlockPos())) <= 64.0 * 64.0);
    }

    @Override
    public ItemStack quickMoveStack(Player clicker, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = slotIndex < LootGroup.MAX_ENTRIES
                ? this.moveItemStackTo(stack, LootGroup.MAX_ENTRIES, this.slots.size(), true)
                : this.moveItemStackTo(stack, 0, LootGroup.MAX_ENTRIES, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Live view on the fixed items of the current page (server side). */
    private final class PageItems implements Container {

        @Override
        public int getContainerSize() {
            return LootGroup.MAX_ENTRIES;
        }

        @Override
        public boolean isEmpty() {
            return LootSetupMenu.this.currentGroup().entries().stream().allMatch(entry -> entry.item().isEmpty());
        }

        @Override
        public ItemStack getItem(int slot) {
            return LootSetupMenu.this.currentGroup().at(slot).map(LootEntry::item).orElse(ItemStack.EMPTY);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack current = this.getItem(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = current.copyWithCount(Math.min(count, current.getCount()));
            this.setItem(slot, current.copyWithCount(current.getCount() - removed.getCount()));
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack current = this.getItem(slot).copy();
            this.setItem(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            LootGroup group = LootSetupMenu.this.currentGroup();
            Optional<LootEntry> existing = group.at(slot);
            boolean clamped = !LootSetupMenu.this.consolationPage();
            if (stack.isEmpty()) {
                if (existing.isPresent() && !existing.get().item().isEmpty()) {
                    LootSetupMenu.this.storeGroup(group.with(new LootEntry(slot, Optional.empty(), ItemStack.EMPTY, 0, 1, 1), clamped));
                }
                return;
            }
            LootEntry entry = existing.map(e -> new LootEntry(slot, Optional.empty(), stack, e.chance(), e.rollsMin(), e.rollsMax()))
                    .orElseGet(() -> LootEntry.ofItem(slot, stack, DEFAULT_CHANCE));
            LootSetupMenu.this.storeGroup(group.with(entry, clamped));
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player clicker) {
            return LootSetupMenu.this.stillValid(clicker);
        }

        @Override
        public void clearContent() {
        }
    }
}
