package com.tpa.tpamod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // ========== /tpa <玩家> 请求传送到对方身边 ==========
        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.getEntity() instanceof Player)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            TpaManager.addRequest(sender, target, false);
                            return 1;
                        })
                )
        );

        // ========== /tpahere <玩家> 请求对方传送到自己身边 ==========
        dispatcher.register(Commands.literal("tpahere")
                .requires(source -> source.getEntity() instanceof Player)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            TpaManager.addRequest(sender, target, true);
                            return 1;
                        })
                )
        );

        // ========== /tpaccept [玩家] 同意请求 ==========
        dispatcher.register(Commands.literal("tpaccept")
                .requires(source -> source.getEntity() instanceof Player)
                .executes(ctx -> { // 无参数，同意最早的请求
                    ServerPlayer acceptor = ctx.getSource().getPlayerOrException();
                    TpaManager.TpaRequest req = TpaManager.getFirstRequestFor(acceptor.getUUID());
                    if (req == null) {
                        acceptor.sendSystemMessage(Component.literal("你没有待处理的传送请求。"));
                        return 0;
                    }
                    return acceptRequest(acceptor, req);
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer acceptor = ctx.getSource().getPlayerOrException();
                            ServerPlayer sender = EntityArgument.getPlayer(ctx, "player");
                            TpaManager.TpaRequest req = TpaManager.getRequestFrom(acceptor.getUUID(), sender.getUUID());
                            if (req == null) {
                                acceptor.sendSystemMessage(Component.literal("没有来自 " + sender.getName().getString() + " 的请求。"));
                                return 0;
                            }
                            return acceptRequest(acceptor, req);
                        })
                )
        );

        // ========== /tpadeny [玩家] 拒绝请求 ==========
        dispatcher.register(Commands.literal("tpadeny")
                .requires(source -> source.getEntity() instanceof Player)
                .executes(ctx -> {
                    ServerPlayer denier = ctx.getSource().getPlayerOrException();
                    TpaManager.TpaRequest req = TpaManager.getFirstRequestFor(denier.getUUID());
                    if (req == null) {
                        denier.sendSystemMessage(Component.literal("没有待处理的传送请求。"));
                        return 0;
                    }
                    return denyRequest(denier, req);
                })
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer denier = ctx.getSource().getPlayerOrException();
                            ServerPlayer sender = EntityArgument.getPlayer(ctx, "player");
                            TpaManager.TpaRequest req = TpaManager.getRequestFrom(denier.getUUID(), sender.getUUID());
                            if (req == null) {
                                denier.sendSystemMessage(Component.literal("没有来自 " + sender.getName().getString() + " 的请求。"));
                                return 0;
                            }
                            return denyRequest(denier, req);
                        })
                )
        );

        // ========== /tpcancel 取消自己发出的所有请求 ==========
        dispatcher.register(Commands.literal("tpcancel")
                .requires(source -> source.getEntity() instanceof Player)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int count = TpaManager.cancelAllFrom(player.getUUID());
                    if (count == 0) {
                        player.sendSystemMessage(Component.literal("你没有待处理的传送请求。"));
                    } else {
                        player.sendSystemMessage(Component.literal("已取消 " + count + " 个传送请求。"));
                    }
                    return 1;
                })
        );

        // ========== /tptoggle 开关接收请求 ==========
        dispatcher.register(Commands.literal("tptoggle")
                .requires(source -> source.getEntity() instanceof Player)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    boolean newState = TpaManager.toggleReceiving(player.getUUID());
                    player.sendSystemMessage(Component.literal("传送请求接收已" + (newState ? "开启" : "关闭") + "。"));
                    return 1;
                })
        );

        // ========== /tpc <x> <y> <z> 直接传送到坐标 ==========
        dispatcher.register(Commands.literal("tpc")
                .requires(source -> source.getEntity() instanceof Player)
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                            double y = DoubleArgumentType.getDouble(ctx, "y");
                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                            player.teleportTo(player.serverLevel(), x, y, z,
                                                    player.getYRot(), player.getXRot());
                                            player.sendSystemMessage(Component.literal("已传送到 " + x + ", " + y + ", " + z));
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    // ---------- 辅助方法：同意请求并传送 ----------
    private static int acceptRequest(ServerPlayer acceptor, TpaManager.TpaRequest req) {
        ServerPlayer target = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(req.from);
        if (target == null) {
            acceptor.sendSystemMessage(Component.literal("发起请求的玩家已离线。"));
            TpaManager.removeRequest(req);
            return 0;
        }
        // 传送：无论是 /tpa 还是 /tpahere，都是请求发起人传送到接受者身边
        target.teleportTo(acceptor.serverLevel(), acceptor.getX(), acceptor.getY(), acceptor.getZ(),
                target.getYRot(), target.getXRot());
        target.sendSystemMessage(Component.literal(acceptor.getName().getString() + " 同意了你的传送请求。"));
        acceptor.sendSystemMessage(Component.literal("传送成功。"));
        TpaManager.removeRequest(req);
        return 1;
    }

    // ---------- 辅助方法：拒绝请求 ----------
    private static int denyRequest(ServerPlayer denier, TpaManager.TpaRequest req) {
        ServerPlayer sender = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(req.from);
        if (sender != null) {
            sender.sendSystemMessage(Component.literal(denier.getName().getString() + " 拒绝了你的传送请求。"));
        }
        denier.sendSystemMessage(Component.literal("已拒绝请求。"));
        TpaManager.removeRequest(req);
        return 1;
    }
}