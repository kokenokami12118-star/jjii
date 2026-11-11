package com.example.bulkbreak;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一括破壊: オンのとき、ブロックを壊すとつながった同じブロックをまとめて壊す。
 * 設定は /bulkbreak コマンドで切り替える(右Shiftで開くUIもこのコマンドを送る)。
 *   /bulkbreak on | off | toggle
 *   /bulkbreak max <1-512>
 *   /bulkbreak add <ブロック> / remove <ブロック>   … 対象ブロックを選ぶ
 *   /bulkbreak addmatch <文字> / removematch <文字>  … IDに文字を含むブロックをまとめて追加/解除
 *   /bulkbreak clear                                 … 選択を空にする(=全ブロックが対象)
 *   /bulkbreak list
 * 対象ブロックが1つも選ばれていないときは、すべてのブロックが対象。
 * 設定はプレイヤーごと・サーバーが動いている間だけ保持(再起動でオフ・64・全ブロックに戻る)。
 */
public class BulkBreakMod implements ModInitializer {
    public static final int DEFAULT_MAX = 64;
    public static final int LIMIT_MAX = 512;

    private static class Settings {
        boolean enabled = false;
        int max = DEFAULT_MAX;
        final Set<Block> allowed = new HashSet<>();
    }

    private static final Map<UUID, Settings> SETTINGS = new HashMap<>();

    /** 一括破壊の中で起きる破壊イベントに、また反応しないための印。 */
    private static boolean breaking = false;

    private static Settings settings(ServerPlayer player) {
        return SETTINGS.computeIfAbsent(player.getUUID(), id -> new Settings());
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                dispatcher.register(Commands.literal("bulkbreak")
                        .then(Commands.literal("on").executes(ctx -> {
                            settings(ctx.getSource().getPlayerOrException()).enabled = true;
                            say(ctx, "一括破壊: ON");
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            settings(ctx.getSource().getPlayerOrException()).enabled = false;
                            say(ctx, "一括破壊: OFF");
                            return 1;
                        }))
                        .then(Commands.literal("toggle").executes(ctx -> {
                            Settings s = settings(ctx.getSource().getPlayerOrException());
                            s.enabled = !s.enabled;
                            say(ctx, "一括破壊: " + (s.enabled ? "ON" : "OFF"));
                            return 1;
                        }))
                        .then(Commands.literal("max")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, LIMIT_MAX))
                                        .executes(ctx -> {
                                            int n = IntegerArgumentType.getInteger(ctx, "count");
                                            settings(ctx.getSource().getPlayerOrException()).max = n;
                                            say(ctx, "一括破壊の上限: " + n + "個");
                                            return 1;
                                        })))
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                        .executes(ctx -> {
                                            Holder.Reference<Block> h = ResourceArgument.getResource(ctx, "block", Registries.BLOCK);
                                            Settings s = settings(ctx.getSource().getPlayerOrException());
                                            s.allowed.add(h.value());
                                            say(ctx, idOf(h.value()) + " を対象に追加 (合計" + s.allowed.size() + "種類)");
                                            return 1;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                        .executes(ctx -> {
                                            Holder.Reference<Block> h = ResourceArgument.getResource(ctx, "block", Registries.BLOCK);
                                            Settings s = settings(ctx.getSource().getPlayerOrException());
                                            s.allowed.remove(h.value());
                                            say(ctx, idOf(h.value()) + " を対象から外した (合計" + s.allowed.size() + "種類)");
                                            return 1;
                                        })))
                        .then(Commands.literal("addmatch")
                                .then(Commands.argument("text", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String text = StringArgumentType.getString(ctx, "text").toLowerCase();
                                            Settings s = settings(ctx.getSource().getPlayerOrException());
                                            int n = 0;
                                            for (Block b : BuiltInRegistries.BLOCK) {
                                                if (idOf(b).contains(text) && s.allowed.add(b)) n++;
                                            }
                                            say(ctx, "「" + text + "」を含む" + n + "種類を追加 (合計" + s.allowed.size() + "種類)");
                                            return n;
                                        })))
                        .then(Commands.literal("removematch")
                                .then(Commands.argument("text", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String text = StringArgumentType.getString(ctx, "text").toLowerCase();
                                            Settings s = settings(ctx.getSource().getPlayerOrException());
                                            int before = s.allowed.size();
                                            s.allowed.removeIf(b -> idOf(b).contains(text));
                                            say(ctx, "「" + text + "」を含む" + (before - s.allowed.size())
                                                    + "種類を外した (合計" + s.allowed.size() + "種類)");
                                            return 1;
                                        })))
                        .then(Commands.literal("clear").executes(ctx -> {
                            settings(ctx.getSource().getPlayerOrException()).allowed.clear();
                            say(ctx, "選択をクリア: すべてのブロックが対象");
                            return 1;
                        }))
                        .then(Commands.literal("list").executes(ctx -> {
                            Settings s = settings(ctx.getSource().getPlayerOrException());
                            if (s.allowed.isEmpty()) {
                                say(ctx, "対象: すべてのブロック");
                            } else {
                                StringBuilder sb = new StringBuilder("対象 " + s.allowed.size() + "種類: ");
                                int shown = 0;
                                for (Block b : s.allowed) {
                                    if (shown++ >= 20) { sb.append("..."); break; }
                                    sb.append(idOf(b).replace("minecraft:", "")).append(" ");
                                }
                                say(ctx, sb.toString());
                            }
                            return 1;
                        }))));

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (breaking) return;
            if (!(level instanceof ServerLevel serverLevel)) return;
            if (!(player instanceof ServerPlayer sp)) return;
            Settings s = SETTINGS.get(sp.getUUID());
            if (s == null || !s.enabled) return;
            if (state.isAir()) return;
            if (!s.allowed.isEmpty() && !s.allowed.contains(state.getBlock())) return;
            bulkBreak(serverLevel, sp, pos, state, s.max);
        });
    }

    private static void bulkBreak(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState state, int max) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);

        int broken = 0;
        breaking = true;
        try {
            while (!queue.isEmpty() && broken < max) {
                BlockPos current = queue.poll();
                // 周囲26方向(斜めも)を調べる
                for (int dx = -1; dx <= 1 && broken < max; dx++) {
                    for (int dy = -1; dy <= 1 && broken < max; dy++) {
                        for (int dz = -1; dz <= 1 && broken < max; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos next = current.offset(dx, dy, dz);
                            if (!visited.add(next)) continue;
                            if (!level.getBlockState(next).is(state.getBlock())) continue;
                            if (toolAboutToBreak(player)) return;
                            if (player.gameMode.destroyBlock(next)) {
                                broken++;
                                queue.add(next);
                            }
                        }
                    }
                }
            }
        } finally {
            breaking = false;
        }
    }

    /** 道具の耐久が残り1以下なら true(道具が壊れる前に止める)。 */
    private static boolean toolAboutToBreak(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }
}
