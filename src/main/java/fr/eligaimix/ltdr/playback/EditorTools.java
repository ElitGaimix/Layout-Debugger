package fr.eligaimix.ltdr.playback;

import fr.eligaimix.ltdr.LayoutDebugger;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Non-placing tools: right-click records an input, never modifies a block on the client. */
public final class EditorTools {
    public static final Item ON = register("power_on");
    public static final Item OFF = register("power_off");
    public static final Item PULSE = register("pulse");

    public interface ClientInput {
        boolean use(
                net.minecraft.world.entity.player.Player player,
                net.minecraft.core.BlockPos pos,
                String mode);
    }

    public static ClientInput clientInput = (player, pos, mode) -> false;

    private EditorTools() {}

    private static Item register(String name) {
        var id = ResourceKey.create(Registries.ITEM, LayoutDebugger.id(name));
        return Registry.register(
                BuiltInRegistries.ITEM, id, new Item(new Item.Properties().setId(id).stacksTo(1)));
    }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            String mode = mode(player.getItemInHand(hand));
            if (mode != null) {
                if (!player.isCreative() || hand != InteractionHand.MAIN_HAND)
                    return InteractionResult.FAIL;
                if (!(player instanceof ServerPlayer)
                        && clientInput.use(player, hit.getBlockPos(), mode))
                    return InteractionResult.FAIL;
                if (player instanceof ServerPlayer serverPlayer) {
                    var editor = LayoutDebugger.service(serverPlayer.level().getServer()).editor();
                    if (player.isShiftKeyDown())
                        editor.request(
                                serverPlayer,
                                new fr.eligaimix.ltdr.network.EditorRequest(
                                        "ignore_warning",
                                        hit.getBlockPos().getX()
                                                + ","
                                                + hit.getBlockPos().getY()
                                                + ","
                                                + hit.getBlockPos().getZ(),
                                        0,
                                        0));
                    else editor.useTool(serverPlayer, hit.getBlockPos(), mode);
                }
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer
                    && LayoutDebugger.service(serverPlayer.level().getServer())
                            .editor()
                            .isOwner(serverPlayer)
                    && !LayoutDebugger.service(serverPlayer.level().getServer())
                            .editor()
                            .mayBuild(serverPlayer)) {
                serverPlayer.sendSystemMessage(
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                        serverPlayer,
                                        "Construction indisponible pendant la lecture ou le"
                                            + " recalcul. Mettre en pause, ou terminer le mode"
                                            + " lecture.")
                                .withStyle(ChatFormatting.YELLOW));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
                (player, level, hand, entity, hit) ->
                        mode(player.getItemInHand(hand)) != null
                                ? InteractionResult.FAIL
                                : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                mode(player.getItemInHand(hand)) != null
                        ? InteractionResult.FAIL
                        : InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) ->
                !(player instanceof ServerPlayer serverPlayer)
                        || !LayoutDebugger.service(level.getServer()).editor().isOwner(serverPlayer)
                        || LayoutDebugger.service(level.getServer())
                                .editor()
                                .mayBuild(serverPlayer));
    }

    public static void give(ServerPlayer player) {
        for (Item item : new Item[] {ON, OFF}) {
            boolean exists = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                if (player.getInventory().getItem(slot).is(item)) {
                    exists = true;
                    break;
                }
            if (!exists && !player.getInventory().add(named(player, new ItemStack(item)))) {
                player.sendSystemMessage(
                        fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                                        player,
                                        "Libérer deux cases d'inventaire puis cliquer sur Outils.")
                                .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static ItemStack named(ServerPlayer player, ItemStack stack) {
        String mode = mode(stack);
        if (mode != null)
            stack.set(
                    net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    fr.eligaimix.ltdr.i18n.ServerLanguage.component(
                            player, mode.equals("on") ? "Layout · Allumer" : "Layout · Éteindre"));
        return stack;
    }

    public static void refreshNames(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            named(player, player.getInventory().getItem(i));
        player.inventoryMenu.broadcastChanges();
    }

    public static String mode(ItemStack stack) {
        if (stack.is(ON)) return "on";
        if (stack.is(OFF)) return "off";
        // Paper uses tagged vanilla items, so vanilla clients need no registry additions.
        var tag =
                stack.getOrDefault(
                                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.EMPTY)
                        .copyTag();
        String tagged = tag.getStringOr("layout_debugger_tool", "");
        if (tagged.equals("on")
                && (stack.is(net.minecraft.world.item.Items.BLAZE_ROD)
                        || stack.is(
                                net.minecraft.world.item.Items.DYE.pick(
                                        net.minecraft.world.item.DyeColor.LIME)))) return "on";
        if (tagged.equals("off")
                && (stack.is(net.minecraft.world.item.Items.STICK)
                        || stack.is(
                                net.minecraft.world.item.Items.DYE.pick(
                                        net.minecraft.world.item.DyeColor.RED)))) return "off";
        // Legacy item remains registered only to preserve existing saved inventories.
        return null;
    }
}
