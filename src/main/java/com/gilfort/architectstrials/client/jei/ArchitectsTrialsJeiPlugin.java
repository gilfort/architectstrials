package com.gilfort.architectstrials.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.client.EquipmentListScreen;
import com.gilfort.architectstrials.client.GhostSlotScreen;
import com.gilfort.architectstrials.client.LootSetupScreen;
import com.gilfort.architectstrials.client.SpawnMarkerScreen;
import com.gilfort.architectstrials.client.TrialSpawnerMarkerScreen;
import com.gilfort.architectstrials.client.VaultMarkerScreen;
import com.gilfort.architectstrials.menu.GhostSlot;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Optional JEI integration (US-39): items can be dragged from the JEI list into every {@link GhostSlot ghost slot}
 * of the marker and loot screens. A drop sets one item; holding Shift while dropping sets a full stack. Only loaded
 * by JEI, so the mod runs unchanged without it.
 */
@JeiPlugin
public class ArchitectsTrialsJeiPlugin implements IModPlugin {

    /** Plugin id. */
    private static final Identifier UID = ArchitectsTrials.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(SpawnMarkerScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(TrialSpawnerMarkerScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(EquipmentListScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(LootSetupScreen.class, new GhostHandler<>());
        registration.addGhostIngredientHandler(VaultMarkerScreen.class, new GhostHandler<>());
    }

    /**
     * Offers every active ghost slot of a screen that accepts the dragged item as a drop target.
     *
     * @param <T> the screen type
     */
    private static final class GhostHandler<T extends Screen & GhostSlotScreen> implements IGhostIngredientHandler<T> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> dragged = ingredient.getItemStack();
            if (dragged.isEmpty() || dragged.get().isEmpty()) {
                return List.of();
            }
            ItemStack stack = dragged.get();
            AbstractContainerMenu menu = screen.ghostMenu();
            List<Target<I>> targets = new ArrayList<>();
            for (Slot candidate : menu.slots) {
                if (candidate instanceof GhostSlot slot && slot.isActive() && slot.accepts(stack)) {
                    Rect2i area = new Rect2i(screen.ghostLeft() + slot.x, screen.ghostTop() + slot.y, 16, 16);
                    targets.add(new Target<>() {
                        @Override
                        public Rect2i getArea() {
                            return area;
                        }

                        @Override
                        public void accept(I dropped) {
                            int count = Minecraft.getInstance().hasShiftDown() ? slot.maxCount(stack) : 1;
                            GhostSlotScreen.send(menu, slot, stack.copyWithCount(count));
                        }
                    });
                }
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
