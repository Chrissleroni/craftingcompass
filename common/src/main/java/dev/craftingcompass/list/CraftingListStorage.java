package dev.craftingcompass.list;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class CraftingListStorage {

    private static final long DEBOUNCE_MS = 500;
    private static final AtomicReference<Path> PENDING_PATH = new AtomicReference<>();
    private static final AtomicReference<Map<Item, Integer>> PENDING_DATA = new AtomicReference<>();
    private static volatile long pendingDeadline = 0;
    private static volatile Thread debouncer;

    private CraftingListStorage() {}

    public static Path resolveCurrentPath() {
        Minecraft mc = Minecraft.getInstance();
        Path base = mc.gameDirectory.toPath().resolve("craftingcompass");

        if(mc.hasSingleplayerServer()) {
            var server = mc.getSingleplayerServer();
            if (server == null) return null;
            String world = server.getWorldData().getLevelName();
            return base.resolve("sp").resolve(sanitize(world)+".json");
        }

        ServerData data = mc.getCurrentServer();
        if (data != null && mc.player != null) {
            UUID uuid = mc.player.getUUID();
            String host = sanitize(data.ip);
            return base.resolve("mp").resolve(host).resolve(uuid + ".json");
        }
        return null;
    }

    public static void load(CraftingList list) {
        Path path = resolveCurrentPath();
        if (path == null || !Files.exists(path)) {
            list.replaceAll(Map.of());
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonObject items = root.has("items") ? root.getAsJsonObject("items") : new JsonObject();

            Map<Item, Integer> loaded = new LinkedHashMap<>();
            for (var entry : items.entrySet()) {
                Identifier id = Identifier.tryParse(entry.getKey());
                if (id == null) continue;
                var holder = BuiltInRegistries.ITEM.get(id);
                if (holder.isEmpty()) continue;
                int qty;
                try { qty = entry.getValue().getAsInt(); }
                catch (Exception e) { continue; }
                if (qty > 0) loaded.put(holder.get().value(), qty);
            }
            list.replaceAll(loaded);
            System.out.println("[CraftingCompass] Loaded list (" + loaded.size() + " entries) from " + path);
        } catch (IOException | RuntimeException e) {
            System.err.println("[CraftingCompass] Failed to load list from " + path + ": " + e);
            list.replaceAll(Map.of());
        }
    }

    public static void scheduleSave(CraftingList list) {
        Path path = resolveCurrentPath();
        if (path == null) return;
        writeAtomically(path, list.snapshot());
    }

    public static void saveNow(CraftingList list) {
        Path path = resolveCurrentPath();
        if (path == null) return;
        writeAtomically(path, list.snapshot());
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
                Map<Item, Integer> d = PENDING_DATA.getAndSet(null);
                if (p != null && d != null) writeAtomically(p, d);
                if (PENDING_DATA.get() == null) return;
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

    private static void writeAtomically(Path path, Map<Item, Integer> data) {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonObject items = new JsonObject();
            for (var e : data.entrySet()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(e.getKey());
                items.addProperty(id.toString(), e.getValue());
            }
            root.add("items", items);

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
