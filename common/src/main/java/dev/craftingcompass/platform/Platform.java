package dev.craftingcompass.platform;

import java.util.ServiceLoader;

public interface Platform {
    String loaderName();
    boolean isModLoaded(String modId);

    static Platform get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        private static final Platform INSTANCE = ServiceLoader.load(Platform.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Platform implementation found on classpath. " +
                        "Check META-INF/services/dev.craftingcompass.platform.Platform"));

        private Holder() {}
    }
}
