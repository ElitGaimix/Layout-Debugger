package fr.eligaimix.ltdr.platform;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.ticks.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric accessors remain the fast path; the pinned Paper backend uses named native members. */
public final class NativeAccess {
    private NativeAccess() {}

    public interface LevelView {
        ObjectLinkedOpenHashSet<BlockEventData> layoutDebugger$blockEvents();

        List<BlockEventData> layoutDebugger$blockEventsToReschedule();

        PersistentEntitySectionManager<Entity> layoutDebugger$entityManager();
    }

    public interface TickView<T> {
        Long2ObjectMap<LevelChunkTicks<T>> layoutDebugger$containers();

        Queue<ScheduledTick<T>> layoutDebugger$toRun();

        Set<ScheduledTick<?>> layoutDebugger$toRunSet();
    }

    public interface ChunkTickView<T> {
        List<SavedTick<T>> layoutDebugger$pending();

        void layoutDebugger$pending(List<SavedTick<T>> pending);

        Set<ScheduledTick<?>> layoutDebugger$positions();
    }

    public interface PistonView {
        boolean layoutDebugger$getNeighborSignal(
                SignalGetter level, BlockPos pos, Direction facing);
    }

    public interface PlayerListView {
        void layoutDebugger$save(ServerPlayer player);
    }

    private static final Map<String, String> FIELDS =
            Map.of(
                    "blockEvents",
                    "blockEvents",
                    "blockEventsToReschedule",
                    "blockEventsToReschedule",
                    "entityManager",
                    "entityManager",
                    "containers",
                    "allContainers",
                    "toRun",
                    "toRunThisTick",
                    "toRunSet",
                    "toRunThisTickSet",
                    "pending",
                    "pendingTicks",
                    "positions",
                    "ticksPerPosition");
    private static final ClassValue<Map<String, AccessibleObject>> MEMBERS =
            new ClassValue<>() {
                @Override
                protected Map<String, AccessibleObject> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    public static LevelView level(ServerLevel level) {
        return view(level, LevelView.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> TickView<T> ticks(LevelTicks<T> ticks) {
        return view(ticks, TickView.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> ChunkTickView<T> chunkTicks(LevelChunkTicks<T> ticks) {
        return view(ticks, ChunkTickView.class);
    }

    public static boolean signal(
            PistonBaseBlock piston, SignalGetter level, BlockPos pos, Direction facing) {
        return view(piston, PistonView.class).layoutDebugger$getNeighborSignal(level, pos, facing);
    }

    public static void save(ServerPlayer player) {
        view(player.level().getServer().getPlayerList(), PlayerListView.class)
                .layoutDebugger$save(player);
    }

    private static <T> T view(Object target, Class<T> type) {
        if (type.isInstance(target)) return type.cast(target);
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            String name = method.getName().substring("layoutDebugger$".length());
                            String fieldName = FIELDS.get(name);
                            try {
                                if (fieldName != null) {
                                    // Paper replaces vanilla's entity manager; its main-thread
                                    // mailbox is driven by managedBlock instead.
                                    if (name.equals("entityManager")
                                            && target.getClass()
                                                    .getName()
                                                    .equals(
                                                            "net.minecraft.server.level.ServerLevel")) {
                                        try {
                                            findField(target.getClass(), fieldName);
                                        } catch (IllegalStateException absent) {
                                            return null;
                                        }
                                    }
                                    var field =
                                            (Field)
                                                    MEMBERS.get(target.getClass())
                                                            .computeIfAbsent(
                                                                    "f:" + fieldName,
                                                                    k ->
                                                                            findField(
                                                                                    target
                                                                                            .getClass(),
                                                                                    fieldName));
                                    if (args == null || args.length == 0) return field.get(target);
                                    field.set(target, args[0]);
                                    return null;
                                }
                                var nativeMethod =
                                        (Method)
                                                MEMBERS.get(target.getClass())
                                                        .computeIfAbsent(
                                                                "m:" + name,
                                                                k ->
                                                                        findMethod(
                                                                                target.getClass(),
                                                                                name,
                                                                                method
                                                                                        .getParameterTypes()));
                                return nativeMethod.invoke(target, args);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                        }));
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            try {
                var f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        throw new IllegalStateException(
                "Membre natif incompatible : " + type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] args) {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            try {
                var m = current.getDeclaredMethod(name, args);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        throw new IllegalStateException(
                "Méthode native incompatible : " + type.getName() + "." + name);
    }
}
