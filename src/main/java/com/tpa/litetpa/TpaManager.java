package com.tpa.tpamod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

public class TpaManager {

    // 一条传送请求
    public static class TpaRequest {
        public final UUID from;    // 发起人UUID
        public final UUID to;      // 目标UUID
        public final long time;    // 发起时间（毫秒）
        public final boolean isHere; // true=让目标传送到发起人面前 (/tpahere)

        public TpaRequest(UUID from, UUID to, long time, boolean isHere) {
            this.from = from;
            this.to = to;
            this.time = time;
            this.isHere = isHere;
        }
    }

    // 存储：每个玩家发了哪些请求
    private static final Map<UUID, List<TpaRequest>> outgoing = new HashMap<>();
    // 存储：每个玩家收到了哪些请求
    private static final Map<UUID, List<TpaRequest>> incoming = new HashMap<>();
    // 哪些玩家关闭了接收请求
    private static final Set<UUID> blockedPlayers = new HashSet<>();

    // ---------- 添加新请求 ----------
    public static void addRequest(ServerPlayer from, ServerPlayer to, boolean isHere) {
        // 检查目标是否接收
        if (blockedPlayers.contains(to.getUUID())) {
            from.sendSystemMessage(Component.literal(to.getName().getString() + " 已关闭传送请求接收。"));
            return;
        }
        long now = System.currentTimeMillis();
        TpaRequest req = new TpaRequest(from.getUUID(), to.getUUID(), now, isHere);

        outgoing.computeIfAbsent(from.getUUID(), k -> new ArrayList<>()).add(req);
        incoming.computeIfAbsent(to.getUUID(), k -> new ArrayList<>()).add(req);

        // 通知双方
        to.sendSystemMessage(Component.literal(
                from.getName().getString() + " 想要" + (isHere ? "你传送到他身边" : "传送到你身边") +
                        "，输入 /tpaccept 同意，/tpadeny 拒绝"));
        from.sendSystemMessage(Component.literal("已向 " + to.getName().getString() + " 发送传送请求"));
    }

    // ---------- 获取发给某玩家的第一个请求 ----------
    public static TpaRequest getFirstRequestFor(UUID playerId) {
        List<TpaRequest> list = incoming.get(playerId);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    // ---------- 获取发给某玩家的、指定发送者的请求 ----------
    public static TpaRequest getRequestFrom(UUID receiverId, UUID senderId) {
        List<TpaRequest> list = incoming.get(receiverId);
        if (list == null) return null;
        for (TpaRequest req : list) {
            if (req.from.equals(senderId)) {
                return req;
            }
        }
        return null;
    }

    // ---------- 删除一条请求 ----------
    public static void removeRequest(TpaRequest req) {
        List<TpaRequest> outList = outgoing.get(req.from);
        if (outList != null) outList.remove(req);
        List<TpaRequest> inList = incoming.get(req.to);
        if (inList != null) inList.remove(req);
    }

    // ---------- 取消某人发出的所有请求（/tpcancel） ----------
    public static int cancelAllFrom(UUID fromId) {
        List<TpaRequest> sent = outgoing.remove(fromId);
        if (sent == null) return 0;
        int count = sent.size();
        for (TpaRequest req : sent) {
            List<TpaRequest> targetList = incoming.get(req.to);
            if (targetList != null) targetList.remove(req);
        }
        return count;
    }

    // ---------- 切换接收状态（/tptoggle） ----------
    public static boolean toggleReceiving(UUID playerId) {
        if (blockedPlayers.contains(playerId)) {
            blockedPlayers.remove(playerId);
            return true; // 返回 true 代表现在可以接收
        } else {
            blockedPlayers.add(playerId);
            return false; // 返回 false 代表已屏蔽
        }
    }

    public static boolean isBlocked(UUID playerId) {
        return blockedPlayers.contains(playerId);
    }

    // ---------- 定时检查过期请求 ----------
    public static void tick() {
        int timeoutSeconds = TpaMod.ConfigHolder.CONFIG.requestTimeoutSeconds.get();
        long timeoutMillis = timeoutSeconds * 1000L;
        long now = System.currentTimeMillis();

        // 收集要移除的请求（遍历时不能直接删除）
        List<TpaRequest> expired = new ArrayList<>();
        for (List<TpaRequest> list : incoming.values()) {
            for (TpaRequest req : list) {
                if (now - req.time > timeoutMillis) {
                    expired.add(req);
                }
            }
        }

        for (TpaRequest req : expired) {
            // 通知双方
            ServerPlayer fromPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(req.from);
            ServerPlayer toPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(req.to);
            if (fromPlayer != null) {
                fromPlayer.sendSystemMessage(Component.literal("你发给 " +
                        (toPlayer != null ? toPlayer.getName().getString() : "玩家") + " 的传送请求已过期"));
            }
            if (toPlayer != null) {
                toPlayer.sendSystemMessage(Component.literal("来自 " +
                        (fromPlayer != null ? fromPlayer.getName().getString() : "玩家") + " 的传送请求已过期"));
            }
            removeRequest(req);
        }
    }
}