package dev.craftingcompass.neoforge;

import dev.craftingcompass.CraftingCompassConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(CraftingCompassConstants.MOD_ID)
public final class CraftingCompassNeoForge {
    
    public CraftingCompassNeoForge(IEventBus modBus) {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(this::onClientSetup);
        }
    }
    
    private void onClientSetup(FMLClientSetupEvent event) {
        ClientSetup.init();
    }
}
