package com.example.bulkbreak;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 一括破壊の操作画面(ボタンと入力欄だけ)。
 * 設定の本体はサーバー側にあり、ここから /bulkbreak コマンドを送って切り替える。
 * 表示中のON/OFFは「この画面から最後に送った状態」(サーバー再起動後はOFFに戻る)。
 */
public class BulkBreakScreen extends Screen {
    private static boolean lastSentEnabled = false;
    private static int lastSentMax = BulkBreakMod.DEFAULT_MAX;

    private Button toggleButton;
    private Button maxButton;
    private EditBox maxBox;

    public BulkBreakScreen() {
        super(Component.literal("一括破壊"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = this.height / 2 - 50;

        toggleButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lastSentEnabled = !lastSentEnabled;
            command(lastSentEnabled ? "bulkbreak on" : "bulkbreak off");
            refresh();
        }).bounds(cx - 100, top, 200, 24).build());

        maxBox = new EditBox(this.font, cx - 100, top + 36, 70, 20, Component.literal("上限"));
        maxBox.setMaxLength(3);
        maxBox.setValue(String.valueOf(lastSentMax));
        addRenderableWidget(maxBox);

        maxButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            lastSentMax = readMax();
            maxBox.setValue(String.valueOf(lastSentMax));
            command("bulkbreak max " + lastSentMax);
            refresh();
        }).bounds(cx - 26, top + 36, 126, 20).build());

        addRenderableWidget(Button.builder(Component.literal("壊すブロックを選ぶ"), b ->
                Minecraft.getInstance().setScreenAndShow(new BlockSelectScreen()))
                .bounds(cx - 100, top + 68, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(cx - 100, top + 94, 200, 20).build());

        refresh();
    }

    private int readMax() {
        try {
            int v = Integer.parseInt(maxBox.getValue());
            return Math.max(1, Math.min(BulkBreakMod.LIMIT_MAX, v));
        } catch (NumberFormatException e) {
            return BulkBreakMod.DEFAULT_MAX;
        }
    }

    private void refresh() {
        toggleButton.setMessage(Component.literal("一括破壊: " + (lastSentEnabled ? "ON" : "OFF")
                + "  (クリックで切替)"));
        maxButton.setMessage(Component.literal("上限を設定 (今: " + lastSentMax + "個)"));
    }

    private void command(String cmd) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand(cmd);
        }
    }
}
