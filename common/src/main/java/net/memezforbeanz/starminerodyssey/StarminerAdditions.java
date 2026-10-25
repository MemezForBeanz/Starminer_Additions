package net.memezforbeanz.starminerodyssey;

import mod.azure.azurelib.render.item.AzItemRendererRegistry;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.memezforbeanz.starminerodyssey.blocks.AbstractGravityFieldBlockEntity;
import net.memezforbeanz.starminerodyssey.gravity.GravityAnchors;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.memezforbeanz.starminerodyssey.item.client.StellarCoreRenderer;
import net.memezforbeanz.starminerodyssey.network.ModNetwork;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipSpawner;
import net.memezforbeanz.starminerodyssey.registry.*;
import org.slf4j.Logger;


import com.mojang.logging.LogUtils;

public class StarminerAdditions {
	public static final String MOD_ID = "starmineradditions";
	public static final Logger LOGGER = LogUtils.getLogger();

	public StarminerAdditions() {
	}

	public static void init() {
		// Initialize registries only (no .get() calls here)
		LOGGER.info("Initializing Starminer Additions...");
		ModFluidProperties.FLUID_PROPERTIES.initialize();
		LOGGER.info("Initialized fluid properties");
		ModFluids.FLUIDS.init();
		LOGGER.info("Initialized fluids");
		ModBlocks.BLOCKS.init();
		LOGGER.info("Initialized blocks");
		ModItems.ITEMS.init();
		LOGGER.info("Initialized items");
		Tabs.TABS.init();
		LOGGER.info("Initialized creative tabs");
		ModBlockEntityTypes.BLOCK_ENTITY_TYPES.init();
		LOGGER.info("Initialized block entity types");
		ModNetwork.init();
		LOGGER.info("Initialized networking");
		GravityAnchors.init();
		LOGGER.info("Initialized gravity anchors");
		// Spawning mobs start out in the gravity of the field they spawn in
		EntityEvent.ADD.register((entity, level) -> {
			if (entity instanceof LivingEntity && !(entity instanceof Player)) {
				AbstractGravityFieldBlockEntity.alignSpawnedEntity(level, entity);
			}
			return EventResult.pass();
		});
		ShipSpawner.init();
		LOGGER.info("Initialized ship spawner");
		LOGGER.info("Starminer Additions initialization complete!");
	}
    public static void initClient() {
        // Register client-side stuff here
        LOGGER.info("Initializing Starminer Additions client...");
        AzItemRendererRegistry.register(StellarCoreRenderer::new, ModItems.STELLAR_CORE.get());
        LOGGER.info("Registered STELLAR_CORE item renderer");
        LOGGER.info("Starminer Additions client initialization complete!");
    }

}