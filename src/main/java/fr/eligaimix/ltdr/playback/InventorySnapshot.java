package fr.eligaimix.ltdr.playback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.mojang.serialization.JsonOps;
import fr.eligaimix.ltdr.platform.NativeAccess;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;

/** Private, deep-copied inventory backup for the temporary editor tool palette. */
public final class InventorySnapshot {
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();
	private final UUID owner;
	private final List<ItemStack> slots;
	private final List<ItemStack> crafting;
	private final ItemStack carried;
	private final int selected;
	private boolean installed;
	private boolean restored;
	private Path backupFile;

	private InventorySnapshot(ServerPlayer player) {
		owner = player.getUUID();
		var inventory = player.getInventory();
		var saved = new ArrayList<ItemStack>(inventory.getContainerSize());
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) saved.add(inventory.getItem(slot).copy());
		slots = List.copyOf(saved);
		var grid = player.inventoryMenu.getCraftSlots();
		var savedCrafting = new ArrayList<ItemStack>(grid.getContainerSize());
		for (int slot = 0; slot < grid.getContainerSize(); slot++) savedCrafting.add(grid.getItem(slot).copy());
		crafting = List.copyOf(savedCrafting);
		carried = player.containerMenu.getCarried().copy();
		selected = inventory.getSelectedSlot();
	}

	private InventorySnapshot(UUID owner, List<ItemStack> slots, List<ItemStack> crafting, ItemStack carried, int selected, Path backupFile) {
		this.owner = owner;
		this.slots = slots.stream().map(ItemStack::copy).toList();
		this.crafting = crafting.stream().map(ItemStack::copy).toList();
		this.carried = carried.copy();
		this.selected = selected;
		this.backupFile = backupFile;
	}

	/** Read-only: capture before changing any item or giving the editor tools. */
	public static InventorySnapshot capture(ServerPlayer player) {
		requireServerThread(player);
		if (player.containerMenu != player.inventoryMenu) {
			throw new IllegalStateException("Fermer le coffre ou le conteneur avant de capturer le montage.");
		}
		return new InventorySnapshot(player);
	}

	/** Save durably before exposing a temporary inventory, so autosave/crashes cannot lose the original. */
	public static InventorySnapshot captureSaved(ServerPlayer player, Path backupDirectory) {
		InventorySnapshot snapshot = capture(player);
		Path file = backupDirectory.toAbsolutePath().resolve(player.getUUID() + ".json");
		if (Files.exists(file)) throw new IllegalStateException("Une sauvegarde d'inventaire attend sa récupération. Rejoindre le monde avant de relancer une capture.");
		try {
			Files.createDirectories(file.getParent());
			JsonObject document = snapshot.toJson(player);
			Path temporary = Files.createTempFile(file.getParent(), "inventory-", ".tmp");
			try {
				ByteBuffer bytes = ByteBuffer.wrap((JSON.toJson(document) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
				try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
					while (bytes.hasRemaining()) channel.write(bytes);
					channel.force(true);
				}
				try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
				catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file); }
			} finally { Files.deleteIfExists(temporary); }
			snapshot.backupFile = file;
			return snapshot;
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Sauvegarde de l'inventaire impossible ; l'inventaire n'a pas été modifié.", exception);
		}
	}

	/** Called on join before starting a session; malformed backups are preserved and never replaced. */
	public static boolean recoverSaved(ServerPlayer player, Path backupDirectory) {
		requireServerThread(player);
		Path file = backupDirectory.toAbsolutePath().resolve(player.getUUID() + ".json");
		if (!Files.exists(file)) return false;
		try {
			JsonObject document = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (document.get("schemaVersion").getAsBigDecimal().intValueExact() != 1 || !player.getUUID().toString().equals(document.get("owner").getAsString())) {
				throw new IllegalArgumentException("Version ou propriétaire de sauvegarde invalide.");
			}
			int selected = document.get("selected").getAsBigDecimal().intValueExact();
			if (selected < 0 || selected >= 9) throw new IllegalArgumentException("Case de barre rapide invalide.");
			List<ItemStack> slots = decodeItems(player, document.getAsJsonArray("slots"), player.getInventory().getContainerSize());
			List<ItemStack> crafting = decodeItems(player, document.getAsJsonArray("crafting"), player.inventoryMenu.getCraftSlots().getContainerSize());
			ItemStack carried = decode(player, document.get("carried"));
			new InventorySnapshot(player.getUUID(), slots, crafting, carried, selected, file).restore(player);
			return true;
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("Récupération de l'inventaire impossible ; sauvegarde conservée : " + file.getFileName(), exception);
		}
	}

	/** Original items remain in this backup; only a temporary palette is exposed. */
	public void installTools(ServerPlayer player) {
		requireOwner(player);
		if (restored) throw new IllegalStateException("L'inventaire d'origine a déjà été restauré.");
		if (installed) return;
		if (player.containerMenu != player.inventoryMenu) {
			throw new IllegalStateException("Fermer le conteneur avant de recevoir les outils d'édition.");
		}
		player.stopUsingItem();
		player.getInventory().clearContent();
		player.inventoryMenu.getCraftSlots().clearContent();
		player.inventoryMenu.getResultSlot().set(ItemStack.EMPTY);
		player.inventoryMenu.setCarried(ItemStack.EMPTY);
		player.getInventory().setSelectedSlot(0);
		EditorTools.give(player);
		installed = true;
		synchronize(player);
	}

	/** Safe to call repeatedly, including while a player's connection is closing. */
	public void restore(ServerPlayer player) {
		requireOwner(player);
		if (restored) { commitRestore(player); return; }
		player.stopUsingItem();
		// Do not let closing an unexpected menu return/drop temporary cursor items.
		player.containerMenu.setCarried(ItemStack.EMPTY);
		if (player.containerMenu != player.inventoryMenu) player.closeContainer();
		var inventory = player.getInventory();
		inventory.clearContent();
		for (int slot = 0; slot < slots.size(); slot++) inventory.setItem(slot, slots.get(slot).copy());
		var grid = player.inventoryMenu.getCraftSlots();
		grid.clearContent();
		for (int slot = 0; slot < crafting.size(); slot++) grid.setItem(slot, crafting.get(slot).copy());
		// slotsChanged recomputes the result; crafting output is derived, never duplicated.
		player.inventoryMenu.slotsChanged(grid);
		player.inventoryMenu.setCarried(carried.copy());
		inventory.setSelectedSlot(selected);
		inventory.setChanged();
		// The server's inventory is restored even if the connection can no longer sync.
		restored = true;
		try { synchronize(player); }
		finally { commitRestore(player); }
	}

	public boolean isRestored() { return restored; }

	private void commitRestore(ServerPlayer player) {
		if (backupFile == null) return;
		try {
			TagValueOutput expected = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
			player.saveWithoutId(expected);
			NativeAccess.save(player);
			Path playerFile = player.level().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
			var saved = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
			for (String field : List.of("Inventory", "equipment", "SelectedItemSlot")) {
				if (!Objects.equals(expected.buildResult().get(field), saved.get(field))) {
					throw new IOException("La sauvegarde du joueur ne contient pas encore l'inventaire restauré.");
				}
			}
			Files.delete(backupFile);
			backupFile = null;
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("L'inventaire est restauré, mais sa sauvegarde reste en attente. Le fichier de secours a été conservé.", exception);
		}
	}

	private JsonObject toJson(ServerPlayer player) {
		JsonObject document = new JsonObject();
		document.addProperty("schemaVersion", 1);
		document.addProperty("owner", owner.toString());
		document.addProperty("selected", selected);
		document.add("slots", encodeItems(player, slots));
		document.add("crafting", encodeItems(player, crafting));
		document.add("carried", encode(player, carried));
		return document;
	}

	private static JsonArray encodeItems(ServerPlayer player, List<ItemStack> items) {
		JsonArray values = new JsonArray();
		items.forEach(item -> values.add(encode(player, item)));
		return values;
	}

	private static JsonElement encode(ServerPlayer player, ItemStack item) {
		return ItemStack.OPTIONAL_CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), item).getOrThrow();
	}

	private static ItemStack decode(ServerPlayer player, JsonElement element) {
		return ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), element).getOrThrow();
	}

	private static List<ItemStack> decodeItems(ServerPlayer player, JsonArray array, int expectedSize) {
		if (array == null || array.size() != expectedSize) throw new IllegalArgumentException("Nombre de cases d'inventaire invalide.");
		var items = new ArrayList<ItemStack>(expectedSize);
		for (JsonElement item : array) items.add(decode(player, item));
		return items;
	}

	private void requireOwner(ServerPlayer player) {
		requireServerThread(player);
		if (!owner.equals(player.getUUID())) throw new IllegalArgumentException("Cet inventaire appartient à un autre joueur.");
	}

	private static void requireServerThread(ServerPlayer player) {
		if (!player.level().getServer().isSameThread()) throw new IllegalStateException("La restauration d'inventaire doit se faire sur le serveur.");
	}

	private static void synchronize(ServerPlayer player) {
		var inventory = player.getInventory();
		inventory.setChanged();
		// InventoryMenu has no visible BODY/SADDLE slots; sync all inventory slots too.
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) player.connection.send(inventory.createInventoryUpdatePacket(slot));
		player.connection.send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
		player.inventoryMenu.broadcastFullState();
	}
}
