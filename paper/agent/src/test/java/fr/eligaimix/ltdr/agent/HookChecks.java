package fr.eligaimix.ltdr.agent;

import fr.eligaimix.ltdr.bridge.HookBus;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.jar.JarFile;

/**
 * Verify stack/dataflow for every transformed native method against the actual mapped Paper JAR.
 */
public final class HookChecks {
    public static void main(String[] args) throws Exception {
        var transformer = new LayoutAgent();
        int methods = 0, classes = 0;
        try (var jar = new JarFile(args[0])) {
            for (String suffix :
                    List.of(
                            "world/level/block/piston/PistonBaseBlock",
                            "server/MinecraftServer",
                            "server/level/ServerLevel",
                            "server/network/ServerCommonPacketListenerImpl",
                            "server/network/ServerGamePacketListenerImpl",
                            "world/level/Level",
                            "server/ServerTickRateManager")) {
                String name = "net/minecraft/" + suffix;
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
                    bytes = input.readAllBytes();
                }
                var output =
                        transformer.transform(
                                HookChecks.class.getClassLoader(), name, null, null, bytes);
                if (output == null || HookBus.failure != null)
                    throw new AssertionError(
                            "Missing transformation: " + name + " " + HookBus.failure);
                var node = new ClassNode();
                new ClassReader(output).accept(node, 0);
                for (var method : node.methods) {
                    boolean hooked = false;
                    for (var instruction : method.instructions)
                        if (instruction instanceof MethodInsnNode call
                                && call.owner.equals("fr/eligaimix/ltdr/bridge/HookBus"))
                            hooked = true;
                    if (hooked) {
                        new Analyzer<>(new BasicVerifier()).analyze(name, method);
                        methods++;
                    }
                }
                classes++;
            }
        }
        if (classes != 7 || methods != 8)
            throw new AssertionError(
                    "Unexpected hook coverage: " + classes + " classes, " + methods + " methods");
        if (HookBus.power(new Object(), new Object()))
            throw new AssertionError("Uninstalled bridge must be inert");
        System.out.println(
                "Paper startup hooks: "
                        + classes
                        + " native classes / "
                        + methods
                        + " methods verified; uninstalled bridge inert.");
    }
}
