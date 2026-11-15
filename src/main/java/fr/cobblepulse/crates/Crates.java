package fr.cobblepulse.crates;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Crates implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_crates";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-Crates");

    @Override
    public void onInitialize() {
        LOG.info("[Crates] init");
    }
}
