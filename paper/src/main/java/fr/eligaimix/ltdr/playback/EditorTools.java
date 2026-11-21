package fr.eligaimix.ltdr.playback;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Vanilla items tagged privately, so no custom registry is required on Paper. */
public final class EditorTools {
    private EditorTools() {}

    public static String mode(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String mode = tag.getStringOr("layout_debugger_tool", "");
        return mode.equals("on")
                                && (stack.is(Items.BLAZE_ROD)
                                        || stack.is(Items.DYE.pick(DyeColor.LIME)))
                        || mode.equals("off")
                                && (stack.is(Items.STICK) || stack.is(Items.DYE.pick(DyeColor.RED)))
                ? mode
                : null;
    }

    public static void give(ServerPlayer player) {
        for (String mode : new String[] {"on", "off"}) {
            boolean found = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                if (mode.equals(mode(player.getInventory().getItem(i)))) {
                    found = true;
                    break;
                }
            if (found) continue;
            var stack =
                    new ItemStack(
                            mode.equals("on")
                                    ? Items.DYE.pick(DyeColor.LIME)
                                    : Items.DYE.pick(DyeColor.RED));
            var tag = new CompoundTag();
            tag.putString("layout_debugger_tool", mode);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(
                    DataComponents.CUSTOM_NAME,
                    fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                            player, mode.equals("on") ? "Layout · Allumer" : "Layout · Éteindre"));
            if (!player.getInventory().add(stack))
                player.sendSystemMessage(Component.literal("Libérer deux cases pour les outils."));
        }
        player.inventoryMenu.broadcastChanges();
    }

    public static void refreshNames(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            String mode = mode(stack);
            if (mode != null)
                stack.set(
                        DataComponents.CUSTOM_NAME,
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                player,
                                mode.equals("on") ? "Layout · Allumer" : "Layout · Éteindre"));
        }
        player.inventoryMenu.broadcastChanges();
    }
}
