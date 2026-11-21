package fr.eligaimix.ltdr.client;

import net.fabricmc.api.ClientModInitializer;
import fr.eligaimix.ltdr.client.editor.EditorClient;

public class LayoutDebuggerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EditorClient.initialize();
	}
}
