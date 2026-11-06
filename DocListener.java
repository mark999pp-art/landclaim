package kr.landclaim;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 땅 문서 사용, 접속 시 처리, 청크 진입 안내. */
public final class DocListener implements Listener {

    private final LandPlugin plugin;

    public DocListener(LandPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────── 접속 ─────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.updateName(p);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            sendResourcePack(p);
            showHere(p, p.getLocation());
        }, 40L);
    }

    private void sendResourcePack(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        byte[] hash = hex(plugin.getConfig().getString("resource-pack.sha1", ""));
        boolean force = plugin.getConfig().getBoolean("resource-pack.force", false);
        UUID packId = UUID.nameUUIDFromBytes("landclaim-pack".getBytes(StandardCharsets.UTF_8));
        p.setResourcePack(packId, url, hash, Component.text("땅 문서 모양 리소스팩"), force);
    }

    private static byte[] hex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() != 40) return null;
        try {
            byte[] out = new byte[20];
            for (int i = 0; i < 20; i++) {
                out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ───────────── 청크 진입 안내 ─────────────

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        boolean sameWorld = from.getWorld() != null && from.getWorld().equals(to.getWorld());
        if (sameWorld
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        showHere(e.getPlayer(), to);
    }

    private void showHere(Player p, Location loc) {
        if (!plugin.isLandWorld(loc.getWorld())) return;
        p.sendActionBar(plugin.describe(p, loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
    }

    // ───────────── 땅 문서 사용 ─────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        String type = plugin.docType(item);
        if (type == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        if (!p.isSneaking()) {
            p.sendActionBar(LandPlugin.text("§e웅크리고(Shift) 우클릭하면 땅 문서가 사용됩니다"));
            return;
        }
        if (!plugin.isLandWorld(p.getWorld())) {
            plugin.msg(p, "§c이 월드에서는 땅 문서를 사용할 수 없습니다.");
            return;
        }

        int cx = p.getLocation().getBlockX() >> 4;
        int cz = p.getLocation().getBlockZ() >> 4;
        Claim existing = plugin.getClaim(cx, cz);

        switch (type) {
            case "personal" -> usePersonal(p, cx, cz, existing);
            case "public" -> usePublic(p, cx, cz, existing);
            case "plunder" -> usePlunder(p, cx, cz, existing);
            default -> { }
        }
    }

    private void usePersonal(Player p, int cx, int cz, Claim existing) {
        if (existing != null) {
            plugin.msg(p, "§c이미 주인이 있는 땅이라 사용할 수 없습니다.");
            return;
        }
        if (!plugin.inRange(cx, cz)) {
            plugin.msg(p, "§c이 땅은 구매 가능한 범위(중심 5×5 청크) 밖입니다.");
            return;
        }
        Claim claim = new Claim(cx, cz, Claim.Type.PLAYER, p.getUniqueId(), p.getName());
        plugin.claims.put(LandPlugin.key(cx, cz), claim);
        plugin.docsOutstanding = Math.max(0, plugin.docsOutstanding - 1);
        plugin.save();

        consumeOne(p);
        celebrate(p, cx, cz, Color.fromRGB(60, 220, 80));
        Bukkit.broadcast(LandPlugin.text(LandPlugin.PREFIX + "§a" + p.getName() + "님이 땅을 구매했습니다! §7(청크 "
                + cx + ", " + cz + ")"));
    }

    private void usePublic(Player p, int cx, int cz, Claim existing) {
        if (!p.hasPermission("land.admin")) {
            plugin.msg(p, "§c관리자만 사용할 수 있는 문서입니다.");
            return;
        }
        if (existing != null) {
            plugin.msg(p, "§c이미 주인이 있는 땅입니다. 먼저 /land admin reclaim 으로 회수하세요.");
            return;
        }
        Claim claim = new Claim(cx, cz, Claim.Type.PUBLIC, null, "공용");
        plugin.claims.put(LandPlugin.key(cx, cz), claim);
        plugin.save();

        consumeOne(p);
        celebrate(p, cx, cz, Color.fromRGB(60, 120, 255));
        Bukkit.broadcast(LandPlugin.text(LandPlugin.PREFIX + "§9청크 (" + cx + ", " + cz + ")이(가) 공용 땅이 되었습니다."));
    }

    private void usePlunder(Player p, int cx, int cz, Claim existing) {
        if (!p.hasPermission("land.admin")) {
            plugin.msg(p, "§c관리자만 사용할 수 있는 문서입니다.");
            return;
        }
        if (existing == null) {
            plugin.msg(p, "§c주인이 없는 땅은 약탈할 수 없습니다.");
            return;
        }
        if (!existing.isPublic() && p.getUniqueId().equals(existing.owner)) {
            plugin.msg(p, "§c이미 내 땅입니다.");
            return;
        }
        String before = existing.isPublic() ? "공용 땅" : existing.ownerName + "님의 땅";
        Claim claim = new Claim(cx, cz, Claim.Type.PLAYER, p.getUniqueId(), p.getName());
        plugin.claims.put(LandPlugin.key(cx, cz), claim);
        plugin.save();

        consumeOne(p);
        celebrate(p, cx, cz, Color.fromRGB(255, 50, 50));
        Bukkit.broadcast(LandPlugin.text(LandPlugin.PREFIX + "§c" + p.getName() + "님이 " + before
                + "을(를) 약탈했습니다! §7(청크 " + cx + ", " + cz + ")"));
    }

    private void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }
    }

    // ───────────── 효과: 레벨업 소리 + 짧은 테두리 파티클 ─────────────

    private void celebrate(Player p, int cx, int cz, Color color) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        World world = p.getWorld();
        double baseY = p.getLocation().getY() + 0.3;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.6f);
        final int x0 = cx << 4;
        final int z0 = cz << 4;

        new BukkitRunnable() {
            int pulses = 0;

            @Override
            public void run() {
                if (pulses++ >= 12) { // 5틱 * 12번 = 약 3초
                    cancel();
                    return;
                }
                for (int i = 0; i <= 16; i++) {
                    for (int dy = 0; dy < 3; dy++) {
                        double y = baseY + dy;
                        world.spawnParticle(Particle.DUST, x0 + i, y, z0, 1, 0, 0, 0, 0, dust);
                        world.spawnParticle(Particle.DUST, x0 + i, y, z0 + 16, 1, 0, 0, 0, 0, dust);
                        world.spawnParticle(Particle.DUST, x0, y, z0 + i, 1, 0, 0, 0, 0, dust);
                        world.spawnParticle(Particle.DUST, x0 + 16, y, z0 + i, 1, 0, 0, 0, 0, dust);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
