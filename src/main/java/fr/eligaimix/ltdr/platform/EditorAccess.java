package fr.eligaimix.ltdr.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.function.Predicate;

/** Platform permission policy; creative mode stays mandatory independently of grants. */
public final class EditorAccess {
    private static Predicate<ServerPlayer> policy =
            p -> p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    private EditorAccess() {}

    public static boolean allowed(ServerPlayer player) {
        return player.isCreative() && policy.test(player);
    }

    public static void install(Predicate<ServerPlayer> permission) {
        policy = java.util.Objects.requireNonNull(permission);
    }

    public static void reset() {
        policy = p -> p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
