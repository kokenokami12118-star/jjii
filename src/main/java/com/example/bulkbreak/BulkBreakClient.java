package com.example.bulkbreak;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** 右Shiftキー(設定で変更可)で一括破壊の操作画面を開く。 */
public class BulkBreakClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("bulk-break", "main"));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bulk-break.open",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.player != null) {
                    mc.setScreenAndShow(new BulkBreakScreen());
                }
            }
        });
    }
}
