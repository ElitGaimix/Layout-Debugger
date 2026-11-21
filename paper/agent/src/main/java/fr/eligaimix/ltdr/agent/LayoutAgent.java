package fr.eligaimix.ltdr.agent;

import fr.eligaimix.ltdr.bridge.HookBus;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;

/** Startup-only, narrowly scoped hooks for the pinned Paper build. No self attachment. */
public final class LayoutAgent implements ClassFileTransformer {
    private static final String BUS = "fr/eligaimix/ltdr/bridge/HookBus";
    private static final Set<String> TARGETS =
            Set.of(
                    "net/minecraft/world/level/block/piston/PistonBaseBlock",
                    "net/minecraft/server/MinecraftServer",
                    "net/minecraft/server/level/ServerLevel",
                    "net/minecraft/server/network/ServerCommonPacketListenerImpl",
                    "net/minecraft/server/network/ServerGamePacketListenerImpl",
                    "net/minecraft/world/level/Level",
                    "net/minecraft/server/ServerTickRateManager");
    // Keep the bootstrap search JAR open for the lifetime of the VM.
    private static JarFile bootstrapJar;

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        Path bridge = Files.createTempFile("layout-debugger-bridge-", ".jar");
        bridge.toFile().deleteOnExit();
        try (var input = LayoutAgent.class.getResourceAsStream("/layout-debugger-bridge.jar")) {
            if (input == null) throw new IllegalStateException("Missing bootstrap bridge");
            Files.copy(input, bridge, StandardCopyOption.REPLACE_EXISTING);
        }
        bootstrapJar = new JarFile(bridge.toFile());
        instrumentation.appendToBootstrapClassLoaderSearch(bootstrapJar);
        instrumentation.addTransformer(new LayoutAgent());
        System.out.println(
                "[Layout Debugger] Startup hooks installed; dormant until the compatible plugin"
                        + " enables them.");
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String name,
            Class<?> redef,
            ProtectionDomain domain,
            byte[] bytes) {
        if (!TARGETS.contains(name)) return null;
        try {
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            Set<String> found = new HashSet<>();
            for (var method : node.methods) {
                if (name.endsWith("/PistonBaseBlock")
                        && method.name.equals("getNeighborSignal")
                        && method.desc.equals(
                                "(Lnet/minecraft/world/level/SignalGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z")) {
                    var head = new InsnList();
                    head.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    head.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    head.add(call("power", "(Ljava/lang/Object;Ljava/lang/Object;)Z"));
                    var pass = new LabelNode();
                    head.add(new JumpInsnNode(Opcodes.IFEQ, pass));
                    head.add(new InsnNode(Opcodes.ICONST_1));
                    head.add(new InsnNode(Opcodes.IRETURN));
                    head.add(pass);
                    head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    method.instructions.insert(head);
                    found.add("power");
                }
                if (name.endsWith("/MinecraftServer")
                        && method.name.equals("tickServer")
                        && method.desc.equals("(Ljava/util/function/BooleanSupplier;)V")) {
                    lifecycle(method, "serverStart", "serverEnd");
                    found.add("server");
                }
                if (name.endsWith("/ServerLevel")
                        && method.name.equals("tick")
                        && method.desc.equals("(Ljava/util/function/BooleanSupplier;)V")) {
                    lifecycle(method, "levelStart", "levelEnd");
                    found.add("level");
                }
                if (name.endsWith("/ServerCommonPacketListenerImpl")
                        && method.name.equals("send")
                        && method.desc.equals(
                                "(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V")) {
                    var head = new InsnList();
                    for (int i = 0; i < 3; i++) head.add(new VarInsnNode(Opcodes.ALOAD, i));
                    head.add(
                            call(
                                    "suppress",
                                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"));
                    var pass = new LabelNode();
                    head.add(new JumpInsnNode(Opcodes.IFEQ, pass));
                    head.add(new InsnNode(Opcodes.RETURN));
                    head.add(pass);
                    head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    method.instructions.insert(head);
                    found.add("packets");
                }
                if (name.endsWith("/ServerGamePacketListenerImpl")
                        && ((method.name.equals("handleUseItemOn")
                                        && method.desc.equals(
                                                "(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V"))
                                || (method.name.equals("handlePlayerAction")
                                        && method.desc.equals(
                                                "(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V")))) {
                    lifecycle(method, "constructionStart", "constructionEnd");
                    found.add(method.name);
                }
                if (name.endsWith("/Level")
                        && method.name.equals("setBlock")
                        && method.desc.equals(
                                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")) {
                    for (var instruction : method.instructions.toArray())
                        if (instruction.getOpcode() == Opcodes.IRETURN) {
                            var hook = new InsnList();
                            hook.add(new InsnNode(Opcodes.DUP));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            hook.add(call("changed", "(ZLjava/lang/Object;Ljava/lang/Object;)V"));
                            method.instructions.insertBefore(instruction, hook);
                        }
                    found.add("blocks");
                }
                if (name.endsWith("/ServerTickRateManager")
                        && method.name.equals("finishTickSprint")
                        && method.desc.equals("()V")) {
                    boolean quiet = false;
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode invoke
                                && invoke.name.equals("createCommandSourceStack")
                                && invoke.desc.equals(
                                        "()Lnet/minecraft/commands/CommandSourceStack;")) {
                            var hook = new InsnList();
                            hook.add(call("quiet", "(Ljava/lang/Object;)Ljava/lang/Object;"));
                            hook.add(
                                    new TypeInsnNode(
                                            Opcodes.CHECKCAST,
                                            "net/minecraft/commands/CommandSourceStack"));
                            method.instructions.insert(instruction, hook);
                            quiet = true;
                        }
                        if (instruction.getOpcode() == Opcodes.RETURN)
                            method.instructions.insertBefore(instruction, one("sprintFinished"));
                    }
                    if (!quiet) throw new IllegalStateException("Sprint report hook absent");
                    found.add("sprint");
                }
            }
            int expected = name.endsWith("/ServerGamePacketListenerImpl") ? 2 : 1;
            if (found.size() != expected)
                throw new IllegalStateException("Expected " + expected + " hooks, found " + found);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            HookBus.transformed.add(name);
            return writer.toByteArray();
        } catch (Throwable exception) {
            HookBus.failure = name + ": " + exception;
            System.err.println("[Layout Debugger] Incompatible hook: " + HookBus.failure);
            return null;
        }
    }

    private static MethodInsnNode call(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, BUS, name, descriptor, false);
    }

    private static InsnList one(String name) {
        var list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(call(name, "(Ljava/lang/Object;)V"));
        return list;
    }

    private static void lifecycle(MethodNode method, String start, String end) {
        for (var instruction : method.instructions.toArray())
            if (instruction.getOpcode() == Opcodes.RETURN)
                method.instructions.insertBefore(instruction, one(end));
        method.instructions.insert(one(start));
    }
}
