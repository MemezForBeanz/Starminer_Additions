package net.memezforbeanz.starminerodyssey.registry;


import com.teamresourceful.resourcefullib.common.item.tabs.ResourcefulCreativeTab;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistries;
import com.teamresourceful.resourcefullib.common.registry.ResourcefulRegistry;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Supplier;

public class Tabs {
    public static final ResourcefulRegistry<CreativeModeTab> TABS;
    public static final Supplier<CreativeModeTab> TAB;

    public Tabs() {
    }

    static {
        TABS = ResourcefulRegistries.create(BuiltInRegistries.CREATIVE_MODE_TAB, StarminerAdditions.MOD_ID);

        // build() registers the tab itself on both loaders (Forge: its own DeferredRegister, Fabric: directly);
        // registering the returned tab again fails on Fabric ("registered twice")
        TAB = new ResourcefulCreativeTab(new ResourceLocation(StarminerAdditions.MOD_ID, "main"))
                .setItemIcon(ModItems.STELLAR_CORE)
                .addRegistry(ModItems.ITEMS)
                .build();
    }
}
