package com.gilfort.architectstrials.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gilfort.architectstrials.marker.EquipmentList;
import com.gilfort.architectstrials.marker.MarkerEquipment;
import com.gilfort.architectstrials.menu.MarkerSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Base block entity of the mob markers: a container of {@link MarkerSlot marker rows}, each holding one spawn egg
 * stack (entity type and count) and six equipment items. Saved with the structure and read by the marker
 * resolvers.
 * <p>
 * Instead of a fixed item, an empty equipment slot may hold a weighted {@link EquipmentList}; a row may have an
 * equipment loot table. Both are combined into a {@link MarkerEquipment} per row.
 */
public abstract class MobMarkerBlockEntity extends BaseContainerBlockEntity {

    private static final String LISTS_TAG = "equipment_lists";
    private static final String TABLES_TAG = "equipment_tables";
    private static final Codec<List<IndexedList>> LISTS_CODEC = IndexedList.CODEC.listOf();
    private static final Codec<List<RowTable>> TABLES_CODEC = RowTable.CODEC.listOf();

    private final int rows;
    private NonNullList<ItemStack> items;
    private final Map<Integer, EquipmentList> lists = new HashMap<>();
    private final Map<Integer, ResourceKey<LootTable>> tables = new HashMap<>();

    /**
     * Creates the block entity.
     *
     * @param type  the block entity type
     * @param pos   the position
     * @param state the block state
     * @param rows  the number of marker rows
     */
    protected MobMarkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int rows) {
        super(type, pos, state);
        this.rows = rows;
        this.items = NonNullList.withSize(rows * MarkerSlot.ROW_SIZE, ItemStack.EMPTY);
    }

    /** @return the number of marker rows */
    public int rows() {
        return this.rows;
    }

    /**
     * Returns the spawn egg stack of a row.
     *
     * @param row the row
     * @return the stack (possibly empty)
     */
    public ItemStack egg(int row) {
        return this.items.get(row * MarkerSlot.ROW_SIZE);
    }

    /**
     * Returns the entity type of a row's spawn egg.
     *
     * @param row the row
     * @return the entity type, or {@code null} if the row holds no spawn egg
     */
    public @Nullable EntityType<?> entityType(int row) {
        ItemStack egg = this.egg(row);
        return egg.getItem() instanceof SpawnEggItem ? SpawnEggItem.getType(egg) : null;
    }

    /**
     * Returns the equipment of a row.
     *
     * @param row the row
     * @return copies of the non-empty equipment items by slot
     */
    public Map<EquipmentSlot, ItemStack> equipment(int row) {
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : MarkerSlot.EQUIPMENT_SLOTS) {
            ItemStack stack = this.items.get(MarkerSlot.indexOf(row, slot));
            if (!stack.isEmpty()) {
                equipment.put(slot, stack.copy());
            }
        }
        return equipment;
    }

    /**
     * Returns the weighted list of an equipment slot.
     *
     * @param index the container index of the equipment slot
     * @return the list (possibly empty)
     */
    public EquipmentList equipmentList(int index) {
        return this.lists.getOrDefault(index, EquipmentList.EMPTY);
    }

    /**
     * Sets the weighted list of an equipment slot; an empty list removes it.
     *
     * @param index the container index of the equipment slot
     * @param list  the list
     */
    public void setEquipmentList(int index, EquipmentList list) {
        if (list.isEmpty()) {
            this.lists.remove(index);
        } else {
            this.lists.put(index, list);
        }
        this.setChanged();
    }

    /**
     * Returns the equipment loot table of a row.
     *
     * @param row the row
     * @return the loot table, if set
     */
    public Optional<ResourceKey<LootTable>> equipmentTable(int row) {
        return Optional.ofNullable(this.tables.get(row));
    }

    /**
     * Sets or clears the equipment loot table of a row.
     *
     * @param row   the row
     * @param table the loot table, or empty to clear it
     */
    public void setEquipmentTable(int row, Optional<ResourceKey<LootTable>> table) {
        table.ifPresentOrElse(key -> this.tables.put(row, key), () -> this.tables.remove(row));
        this.setChanged();
    }

    /**
     * Returns the complete equipment configuration of a row: fixed items, weighted lists and equipment table.
     *
     * @param row the row
     * @return the configuration
     */
    public MarkerEquipment markerEquipment(int row) {
        Map<EquipmentSlot, EquipmentList> rowLists = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : MarkerSlot.EQUIPMENT_SLOTS) {
            EquipmentList list = this.equipmentList(MarkerSlot.indexOf(row, slot));
            if (!list.isEmpty()) {
                rowLists.put(slot, list);
            }
        }
        return new MarkerEquipment(this.equipment(row), rowLists, this.equipmentTable(row));
    }

    /**
     * Returns a read-only view showing, per container index, the most likely item of the slot's weighted list
     * (empty if the slot has no list). Synchronized to the marker menu for the slot previews.
     *
     * @return the preview container
     */
    public Container previewContainer() {
        return new PreviewContainer();
    }

    /**
     * Returns a live container view of the weighted list of an equipment slot (one item per position), used by
     * the list menu. Changes are stored immediately.
     *
     * @param index the container index of the equipment slot
     * @return the list container
     */
    public Container listContainer(int index) {
        return new ListContainer(index);
    }

    /**
     * Returns the chances of the weighted list of an equipment slot (tenths of a percent per position), used by
     * the list menu. Setting a chance clamps it so the list stays at or below 100 %.
     *
     * @param index the container index of the equipment slot
     * @return the chances
     */
    public ContainerData listChances(int index) {
        return new ContainerData() {
            @Override
            public int get(int position) {
                return MobMarkerBlockEntity.this.equipmentList(index).at(position).map(EquipmentList.Entry::chance).orElse(0);
            }

            @Override
            public void set(int position, int value) {
                EquipmentList list = MobMarkerBlockEntity.this.equipmentList(index);
                list.at(position).ifPresent(entry ->
                        MobMarkerBlockEntity.this.setEquipmentList(index, list.with(position, entry.item(), value)));
            }

            @Override
            public int getCount() {
                return EquipmentList.MAX_ENTRIES;
            }
        };
    }

    /** @return {@code true} if at least one row holds a spawn egg */
    public boolean hasAnyEgg() {
        for (int row = 0; row < this.rows; row++) {
            if (this.entityType(row) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    /**
     * The contents are ghost copies (US-39): hoppers and other automation can neither insert nor extract.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return false;
    }

    /**
     * Markers are editor tools filled from the creative inventory: their contents are never dropped when the
     * marker is broken or resolved.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.rows * MarkerSlot.ROW_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.lists.clear();
        input.read(LISTS_TAG, LISTS_CODEC).ifPresent(entries -> entries.forEach(entry -> this.lists.put(entry.index(), entry.list())));
        this.tables.clear();
        input.read(TABLES_TAG, TABLES_CODEC).ifPresent(entries -> entries.forEach(entry -> this.tables.put(entry.row(), entry.table())));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        if (!this.lists.isEmpty()) {
            List<IndexedList> entries = new ArrayList<>();
            this.lists.forEach((index, list) -> entries.add(new IndexedList(index, list)));
            output.store(LISTS_TAG, LISTS_CODEC, entries);
        }
        if (!this.tables.isEmpty()) {
            List<RowTable> entries = new ArrayList<>();
            this.tables.forEach((row, table) -> entries.add(new RowTable(row, table)));
            output.store(TABLES_TAG, TABLES_CODEC, entries);
        }
    }

    /** A weighted list stored for a container index. */
    private record IndexedList(int index, EquipmentList list) {
        static final Codec<IndexedList> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("index").forGetter(IndexedList::index),
                EquipmentList.CODEC.fieldOf("list").forGetter(IndexedList::list)
        ).apply(instance, IndexedList::new));
    }

    /** An equipment loot table stored for a row. */
    private record RowTable(int row, ResourceKey<LootTable> table) {
        static final Codec<RowTable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("row").forGetter(RowTable::row),
                ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("table").forGetter(RowTable::table)
        ).apply(instance, RowTable::new));
    }

    /** Read-only container of the list previews (see {@link #previewContainer()}). */
    private final class PreviewContainer implements Container {

        @Override
        public int getContainerSize() {
            return MobMarkerBlockEntity.this.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return MobMarkerBlockEntity.this.lists.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return MobMarkerBlockEntity.this.equipmentList(slot).mostLikely();
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return MobMarkerBlockEntity.this.stillValid(player);
        }

        @Override
        public void clearContent() {
        }
    }

    /** Live container view of one weighted list (see {@link #listContainer(int)}). */
    private final class ListContainer implements Container {

        /** Chance given to a newly added item: 10 %, or what is left up to 100 %. */
        private static final int DEFAULT_CHANCE = 100;

        private final int index;

        ListContainer(int index) {
            this.index = index;
        }

        private EquipmentList list() {
            return MobMarkerBlockEntity.this.equipmentList(this.index);
        }

        @Override
        public int getContainerSize() {
            return EquipmentList.MAX_ENTRIES;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return this.list().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.list().at(slot).map(EquipmentList.Entry::item).orElse(ItemStack.EMPTY);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return this.removeItemNoUpdate(slot);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = this.getItem(slot).copy();
            MobMarkerBlockEntity.this.setEquipmentList(this.index, this.list().with(slot, ItemStack.EMPTY, 0));
            return removed;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            EquipmentList list = this.list();
            int chance = list.at(slot).map(EquipmentList.Entry::chance).orElse(DEFAULT_CHANCE);
            MobMarkerBlockEntity.this.setEquipmentList(this.index, list.with(slot, stack.copyWithCount(Math.min(1, stack.getCount())), chance));
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return MarkerSlot.accepts(this.index, stack);
        }

        @Override
        public void setChanged() {
            MobMarkerBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return MobMarkerBlockEntity.this.stillValid(player);
        }

        @Override
        public void clearContent() {
            MobMarkerBlockEntity.this.setEquipmentList(this.index, EquipmentList.EMPTY);
        }
    }
}
