package dev.craftingcompass.client;

import dev.craftingcompass.list.CraftingList;
import dev.craftingcompass.list.CraftingListHolder;
import dev.craftingcompass.recipe.AggregateResolver;
import dev.craftingcompass.recipe.RecipeProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class SidebarController {

    public static final SidebarController INSTANCE = new SidebarController();

    private volatile AggregateResolver.Aggregate latest = new AggregateResolver
            .Aggregate(java.util.List.of(), java.util.List.of());
    private volatile boolean computing = false;

    private final AtomicReference<Long> generation = new AtomicReference<>(0L);
    private Thread worker;
    private Supplier<RecipeProvider> providerSupplier;

    private SidebarController() {}

    public synchronized void install(Supplier<RecipeProvider> providerSupplier) {
        if (this.providerSupplier != null) return;
        this.providerSupplier = providerSupplier;
        CraftingListHolder.get().addListener(this::onListChanged);
        scheduleRecompute();
    }

    public AggregateResolver.Aggregate latest() { return latest; }
    public boolean isComputing() { return computing; }

    private void onListChanged(CraftingList list) {
        scheduleRecompute();
    }

    public void scheduleRecompute() {
        long gen = generation.updateAndGet(g -> g + 1);
        ensureWorker();
        synchronized (this) { notifyAll(); }
    }

    private synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::workerLoop, "CraftingCompass-Aggregate");
        worker.setDaemon(true);
        worker.start();
    }

    private void workerLoop() {
        long lastSeenGen = -1;
        while (!Thread.currentThread().isInterrupted()) {
            long currentGen;
            synchronized (this) {
                try {
                    while ((currentGen = generation.get()) == lastSeenGen) {
                        wait(5_000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            lastSeenGen = currentGen;

            try {
                computing = true;
                RecipeProvider provider = providerSupplier == null ? null : providerSupplier.get();
                if (provider == null || provider.totalRecipeCount() == 0) {
                    Thread.sleep(500);
                    generation.updateAndGet(g -> g + 1);
                    continue;
                }
                AggregateResolver agg = new AggregateResolver(provider);
                AggregateResolver.Aggregate result = agg.resolveAll(CraftingListHolder.get());
                latest = result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                computing = false;
            }
        }
    }

    public synchronized void shutdown() {
        if (worker != null) worker.interrupt();
        worker = null;
        providerSupplier = null;
    }
}
