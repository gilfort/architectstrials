package com.gilfort.architectstrials.client;

import com.gilfort.architectstrials.ArchitectsTrials;
import com.gilfort.architectstrials.editor.Selection;
import com.gilfort.architectstrials.editor.SelectionToolItem;
import com.gilfort.architectstrials.registry.ModDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Draws the selection of a held {@link SelectionToolItem} with vanilla gizmos: both corners and, once complete,
 * the selected box — white if it can be imported, red if it exceeds the maximum footprint.
 */
@EventBusSubscriber(modid = ArchitectsTrials.MOD_ID, value = Dist.CLIENT)
public final class SelectionOutline {

    private static final GizmoStyle FIRST_CORNER = GizmoStyle.stroke(0xFFFF5555, 3.0F);
    private static final GizmoStyle SECOND_CORNER = GizmoStyle.stroke(0xFF5599FF, 3.0F);
    private static final GizmoStyle BOX = GizmoStyle.stroke(0xFFFFFFFF, 2.0F);
    private static final GizmoStyle BOX_TOO_LARGE = GizmoStyle.stroke(0xFFFF3333, 2.0F);

    private SelectionOutline() {
    }

    /**
     * Adds the selection gizmos for this tick (gizmos are collected during the client tick).
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem().getItem() instanceof SelectionToolItem ? player.getMainHandItem()
                : player.getOffhandItem().getItem() instanceof SelectionToolItem ? player.getOffhandItem() : ItemStack.EMPTY;
        Selection selection = tool.get(ModDataComponents.SELECTION.get());
        if (selection == null || !selection.dimension().equals(player.level().dimension())) {
            return;
        }
        selection.first().ifPresent(pos -> Gizmos.cuboid(pos, 0.02F, FIRST_CORNER));
        selection.second().ifPresent(pos -> Gizmos.cuboid(pos, 0.02F, SECOND_CORNER));
        selection.box().ifPresent(box -> Gizmos.cuboid(AABB.of(box), SelectionToolItem.tooLarge(box) ? BOX_TOO_LARGE : BOX));
    }
}
