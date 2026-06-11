package meow.meow;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntiSsid implements ModInitializer {
	public static final String MOD_ID = "anti-ssid";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("anti-ssid loaded");
	}
}
