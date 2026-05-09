package dev.craftingcompass.list;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class CraftingListStorage {

    /** Snapshot of everything we persist. */
    public record SaveData(
            Map<Item, Integer> items,
            Set<Item> completedItems,
            Set<TagKey<Item>> completedTags
    ) {}

    public interface SaveSnapshotProvider {
        SaveData snapshot();
    }

    public interface SaveDataSink {
        void apply(SaveData data);
    }

    private static final long DEBOUNCE_MS = 500;
    private static final AtomicReference<Path> PENDING_PATH = new AtomicReference<>();
    private static final AtomicReference<SaveData> PENDING_DATA = new AtomicReference<>();
    private static volatile long pendingDeadline = 0;
    private static volatile Thread debouncer;

    private CraftingListStorage() {}

    public static Path resolveCurrentPath() {
        Minecraft mc = Minecraft.getInstance();
        Path base = mc.gameDirectory.toPath().resolve("craftingcompass");

        if (mc.hasSingleplayerServer()) {
            var server = mc.getSingleplayerServer();
            if (server == null) return null;
            String world = server.getWorldData().getLevelName();
            return base.resolve("sp").resolve(sanitize(world) + ".json");
        }

        ServerData data = mc.getCurrentServer();
        if (data != null && mc.player != null) {
            UUID uuid = mc.player.getUUID();
            String host = sanitize(data.ip);
            return base.resolve("mp").resolve(host).resolve(uuid + ".json");
        }
        return null;
    }

    public static void load(CraftingList list, SaveDataSink completionSink) {
        Path path = resolveCurrentPath();
        SaveData empty = new SaveData(Map.of(), Set.of(), Set.of());
        if (path == null || !Files.exists(path)) {
            list.replaceAll(empty.items());
            completionSink.apply(empty);
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();

            Map<Item, Integer> loadedItems = new LinkedHashMap<>();
            if (root.has("items") && root.get("items").isJsonObject()) {
                JsonObject items = root.getAsJsonObject("items");
                for (var entry : items.entrySet()) {
                    Identifier id = Identifier.tryParse(entry.getKey());
                    if (id == null) continue;
                    var holder = BuiltInRegistries.ITEM.get(id);
                    if (holder.isEmpty()) continue;
                    int qty;
                    try { qty = entry.getValue().getAsInt(); }
                    catch (Exception e) { continue; }
                    if (qty > 0) loadedItems.put(holder.get().value(), qty);
                }
            }

            Set<Item> loadedCompletedItems = new HashSet<>();
            if (root.has("completedItems") && root.get("completedItems").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("completedItems");
                for (int i = 0; i < arr.size(); i++) {
                    String s = arr.get(i).getAsString();
                    Identifier id = Identifier.tryParse(s);
                    if (id == null) continue;
                    var holder = BuiltInRegistries.ITEM.get(id);
                    if (holder.isEmpty()) continue;
                    loadedCompletedItems.add(holder.get().value());
                }
            }

            Set<TagKey<Item>> loadedCompletedTags = new HashSet<>();
            if (root.has("completedTags") && root.get("completedTags").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("completedTags");
                for (int i = 0; i < arr.size(); i++) {
                    String s = arr.get(i).getAsString();
                    Identifier id = Identifier.tryParse(s);
                    if (id == null) continue;
                    loadedCompletedTags.add(TagKey.create(Registries.ITEM, id));
                }
            }

            list.replaceAll(loadedItems);
            completionSink.apply(new SaveData(loadedItems, loadedCompletedItems, loadedCompletedTags));
            System.out.println("[CraftingCompass] Loaded list ("
                    + loadedItems.size() + " entries, "
                    + loadedCompletedItems.size() + " completed items, "
                    + loadedCompletedTags.size() + " completed tags) from " + path);
        } catch (IOException | RuntimeException e) {
            System.err.println("[CraftingCompass] Failed to load list from " + path + ": " + e);
            list.replaceAll(empty.items());
            completionSink.apply(empty);
        }
    }

    public static void scheduleSave(SaveSnapshotProvider provider) {
        Path path = resolveCurrentPath();
        if (path == null) return;

        PENDING_PATH.set(path);
        PENDING_DATA.set(provider.snapshot());
        pendingDeadline = System.currentTimeMillis() + DEBOUNCE_MS;
        ensureDebouncer();
    }

    public static void saveNow(SaveSnapshotProvider provider) {
        Path path = resolveCurrentPath();
        if (path == null) return;
        SaveData snap = provider.snapshot();
        Thread t = new Thread(() -> writeAtomically(path, snap), "CraftingCompass-FinalSave");
        t.setDaemon(true);
        t.start();
        try { t.join(2000); } catch (InterruptedException ignored) {}
    }

    public static void shutdown() {
        Path p = PENDING_PATH.getAndSet(null);
        SaveData d = PENDING_DATA.getAndSet(null);
        if (p != null && d != null) {
            Thread t = new Thread(() -> writeAtomically(p, d), "CraftingCompass-ShutdownSave");
            t.setDaemon(true);
            t.start();
            try { t.join(2000); } catch (InterruptedException ignored) {}
        }
        if (debouncer != null) debouncer.interrupt();
    }

    private static synchronized void ensureDebouncer() {
        if (debouncer != null && debouncer.isAlive()) return;
        debouncer = new Thread(() -> {
            while (true) {
                try {
                    long now = System.currentTimeMillis();
                    long wait = pendingDeadline - now;
                    if (wait > 0) {
                        Thread.sleep(wait);
                        continue;
                    }
                    Path p = PENDING_PATH.getAndSet(null);
                    SaveData d = PENDING_DATA.getAndSet(null);
                    if (p != null && d != null) writeAtomically(p, d);
                    if (PENDING_PATH.get() == null) return;
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    t.printStackTrace();
                    return;
                }
            }
        }, "CraftingCompass-ListSaver");
        debouncer.setDaemon(true);
        debouncer.start();
    }

    private static void writeAtomically(Path path, SaveData data) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();

            JsonObject items = new JsonObject();
            for (var e : data.items().entrySet()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(e.getKey());
                items.addProperty(id.toString(), e.getValue());
            }
            root.add("items", items);

            JsonArray completedItems = new JsonArray();
            for (Item it : data.completedItems()) {
                completedItems.add(BuiltInRegistries.ITEM.getKey(it).toString());
            }
            root.add("completedItems", completedItems);

            JsonArray completedTags = new JsonArray();
            for (TagKey<Item> tk : data.completedTags()) {
                completedTags.add(tk.location().toString());
            }
            root.add("completedTags", completedTags);

            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[CraftingCompass] Failed to save list to " + path + ": " + e);
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "_";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}