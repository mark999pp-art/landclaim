package kr.landclaim;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 팔린 땅(개인/공용) 보호. land.bypass 권한이 있으면(기본: OP) 어디서든 가능. */
public final class ProtectionListener implements Listener {

    private final LandPlugin plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public ProtectionListener(LandPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────── 판정 도우미 ─────────────

    private boolean allowed(Player p, Claim c) {
        if (c == null) return true;
        if (p.hasPermission("land.bypass")) return true;
        if (c.isPublic()) return false;
        if (p.getUniqueId().equals(c.owner)) return true;
        return plugin.isTrusted(c.owner, p.getUniqueId());
    }

    /** 허용이면 true. 막히면 안내(1초에 한 번)를 보내고 false. */
    private boolean check(Player p, Location loc) {
        Claim c = plugin.claimAt(loc);
        if (allowed(p, c)) return true;
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last == null || now - last > 1000L) {
            lastWarn.put(p.getUniqueId(), now);
            String who = c.isPublic() ? "공용 땅" : c.ownerName + "님의 땅";
            p.sendActionBar(LandPlugin.text("§c" + who + "에서는 할 수 없습니다"));
        }
        return false;
    }

    private Player playerFrom(Entity damager) {
        if (damager instanceof Player pl) return pl;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player pl) return pl;
        }
        return null;
    }

    // ───────────── 블록 ─────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!check(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!check(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!check(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!check(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        Claim c = plugin.claimAt(b.getLocation());
        if (c == null) return;
        Player p = e.getPlayer();
        if (allowed(p, c)) return;

        if (e.getAction() == Action.PHYSICAL) {
            Material m = b.getType();
            if (m == Material.FARMLAND || m == Material.TURTLE_EGG) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        boolean interactable = b.getType().isInteractable();
        boolean spawnEgg = item != null && item.getType().name().endsWith("_SPAWN_EGG");
        if (interactable || spawnEgg) {
            e.setCancelled(true);
            check(p, b.getLocation()); // 안내 메시지 표시
        }
    }

    // ───────────── 폭발 / 불 / 몹 ─────────────

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.isProtected(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.isProtected(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (plugin.isProtected(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p != null) {
            if (!check(p, e.getBlock().getLocation())) e.setCancelled(true);
        } else if (plugin.isProtected(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        boolean griefer = en instanceof Enderman || en instanceof Wither || en instanceof Ravager
                || en instanceof Silverfish || en instanceof Zombie;
        if (griefer && plugin.isProtected(e.getBlock().getLocation())) e.setCancelled(true);
    }

    // ───────────── 액자 / 갑옷 거치대 / 동물 ─────────────

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = playerFrom(e.getRemover());
        if (p != null) {
            if (!check(p, e.getEntity().getLocation())) e.setCancelled(true);
        } else if (plugin.isProtected(e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p != null && !check(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (en instanceof Hanging || en instanceof ArmorStand) {
            if (!check(e.getPlayer(), en.getLocation())) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!check(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (victim instanceof Player || victim instanceof Enemy) return;
        Player attacker = playerFrom(e.getDamager());
        if (attacker == null) return;
        if (!check(attacker, victim.getLocation())) e.setCancelled(true);
    }

    // ───────────── 액체 / 피스톤 (땅 경계 넘기 방지) ─────────────

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        Claim to = plugin.claimAt(e.getToBlock().getLocation());
        if (to == null) return;
        Claim from = plugin.claimAt(e.getBlock().getLocation());
        if (from == null || !from.ownerKey().equals(to.ownerKey())) e.setCancelled(true);
    }

    private boolean pistonMayTouch(Block piston, Block target) {
        Claim t = plugin.claimAt(target.getLocation());
        if (t == null) return true;
        Claim from = plugin.claimAt(piston.getLocation());
        return from != null && from.ownerKey().equals(t.ownerKey());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        List<Block> moved = e.getBlocks();
        Block piston = e.getBlock();
        // 밀려 나갈 첫 칸(피스톤 바로 앞) 포함
        if (!pistonMayTouch(piston, piston.getRelative(e.getDirection()))) {
            e.setCancelled(true);
            return;
        }
        for (Block b : moved) {
            if (!pistonMayTouch(piston, b) || !pistonMayTouch(piston, b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        Block piston = e.getBlock();
        for (Block b : e.getBlocks()) {
            if (!pistonMayTouch(piston, b)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
