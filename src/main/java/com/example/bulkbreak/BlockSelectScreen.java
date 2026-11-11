package com.example.bulkbreak;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 一括破壊の対象ブロックを選ぶ画面。
 * ブロックをクリックで 追加/解除 を切り替え。何も選ばなければ全ブロックが対象。
 * 検索欄に入れた文字(日本語名 または ID)で絞り込める。
 * 表示中の✔は「この画面から最後に送った状態」(サーバー再起動後は空に戻る)。
 */
public class BlockSelectScreen extends Screen {
    private static final int PER_PAGE = 7;

    /** この画面から送った選択(ブロックID)。 */
    private static final Set<String> SELECTED = new HashSet<>();
    private static String lastSearch = "";

    private final List<Block> filtered = new ArrayList<>();
    private int page = 0;

    private EditBox searchBox;
    private Button[] listButtons;
    private Button pageInfo;
    private Button addMatchButton;
    private Button removeMatchButton;

    public BlockSelectScreen() {
        super(Component.literal("壊すブロックを選ぶ"));
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(4, (this.height - 246) / 2);

        searchBox = new EditBox(this.font, cx - 100, top, 130, 20, Component.literal("検索"));
        searchBox.setMaxLength(40);
        searchBox.setValue(lastSearch);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("検索"), b -> {
            lastSearch = searchBox.getValue();
            page = 0;
            applyFilter();
            refresh();
        }).bounds(cx + 34, top, 66, 20).build());

        listButtons = new Button[PER_PAGE];
        for (int i = 0; i < PER_PAGE; i++) {
            final int slot = i;
            listButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> toggle(slot))
                    .bounds(cx - 100, top + 24 + i * 22, 200, 20).build());
        }

        int pageRow = top + 24 + PER_PAGE * 22;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (page > 0) page--;
            refresh();
        }).bounds(cx - 100, pageRow, 30, 20).build());
        pageInfo = addRenderableWidget(Button.builder(Component.empty(), b -> { })
                .bounds(cx - 66, pageRow, 132, 20).build());
        pageInfo.active = false;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if ((page + 1) * PER_PAGE < filtered.size()) page++;
            refresh();
        }).bounds(cx + 70, pageRow, 30, 20).build());

        int matchRow = pageRow + 22;
        addMatchButton = addRenderableWidget(Button.builder(Component.literal("IDに検索語を含む全部を追加"), b -> {
            String t = matchText();
            if (!t.isEmpty()) {
                command("bulkbreak addmatch " + t);
                for (Block blk : BuiltInRegistries.BLOCK) {
                    if (idOf(blk).contains(t)) SELECTED.add(idOf(blk));
                }
                refresh();
            }
        }).bounds(cx - 100, matchRow, 100, 20).build());
        removeMatchButton = addRenderableWidget(Button.builder(Component.literal("全部を外す"), b -> {
            String t = matchText();
            if (!t.isEmpty()) {
                command("bulkbreak removematch " + t);
                SELECTED.removeIf(id -> id.contains(t));
                refresh();
            }
        }).bounds(cx + 2, matchRow, 98, 20).build());

        int lastRow = matchRow + 22;
        addRenderableWidget(Button.builder(Component.literal("選択を空に(全ブロック対象)"), b -> {
            command("bulkbreak clear");
            SELECTED.clear();
            refresh();
        }).bounds(cx - 100, lastRow, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("戻る"), b ->
                Minecraft.getInstance().setScreenAndShow(new BulkBreakScreen()))
                .bounds(cx + 44, lastRow, 56, 20).build());

        applyFilter();
        refresh();
    }

    /** 検索語をIDに使える形(半角英数と_のみ)にしたもの。日本語などのときは空。 */
    private String matchText() {
        String t = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        return t.matches("[a-z0-9_]+") ? t : "";
    }

    private void applyFilter() {
        filtered.clear();
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (Block b : BuiltInRegistries.BLOCK) {
            String id = idOf(b);
            if (id.equals("minecraft:air") || id.endsWith("_air")) continue;
            if (q.isEmpty()
                    || id.contains(q)
                    || b.getName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(b);
            }
        }
        filtered.sort((a, c) -> idOf(a).compareTo(idOf(c)));
    }

    private void toggle(int slot) {
        int idx = page * PER_PAGE + slot;
        if (idx >= filtered.size()) return;
        String id = idOf(filtered.get(idx));
        if (SELECTED.contains(id)) {
            SELECTED.remove(id);
            command("bulkbreak remove " + id);
        } else {
            SELECTED.add(id);
            command("bulkbreak add " + id);
        }
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            Button b = listButtons[i];
            if (idx < filtered.size()) {
                Block blk = filtered.get(idx);
                b.visible = true;
                Component name = blk.getName();
                b.setMessage(SELECTED.contains(idOf(blk))
                        ? Component.literal("✔ ").append(name)
                        : name);
            } else {
                b.visible = false;
            }
        }
        int pages = Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
        pageInfo.setMessage(Component.literal((page + 1) + "/" + pages + "  選択" + SELECTED.size()));
        boolean canMatch = !matchText().isEmpty();
        addMatchButton.active = canMatch;
        removeMatchButton.active = canMatch;
    }

    private void command(String cmd) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.connection.sendCommand(cmd);
        }
    }
}
