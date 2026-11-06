package kr.landclaim;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 5x5 땅 지도 창. 초록 = 구매 가능, 빨강 = 팔린 땅, 파랑 = 공용 땅. */
public final class MapGui implements InventoryHolder {

    private final Inventory inventory;

    public MapGui(LandPlugin plugin, Player p) {
        inventory = Bukkit.createInventory(this, 45, Component.text("땅 지도"));

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>(), false);
        for (int i = 0; i < 45; i++) inventory.setItem(i, filler);

        boolean here = plugin.isLandWorld(p.getWorld());
        int pcx = p.getLocation().getBlockX() >> 4;
        int pcz = p.getLocation().getBlockZ() >> 4;

        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int cx = plugin.centerX() - LandPlugin.RADIUS + c;
                int cz = plugin.centerZ() - LandPlugin.RADIUS + r;
                Claim claim = plugin.getClaim(cx, cz);

                Material mat;
                String state;
                if (claim == null) {
                    mat = Material.LIME_STAINED_GLASS_PANE;
                    state = "§a구매 가능";
                } else if (claim.isPublic()) {
                    mat = Material.BLUE_STAINED_GLASS_PANE;
                    state = "§9공용 땅";
                } else {
                    mat = Material.RED_STAINED_GLASS_PANE;
                    state = "§c" + claim.ownerName + "님의 땅";
                }

                boolean current = here && cx == pcx && cz == pcz;
                List<String> lore = new ArrayList<>();
                lore.add("§7청크 (" + cx + ", " + cz + ")");
                lore.add("§7블록 x " + (cx << 4) + " ~ " + ((cx << 4) + 15)
                        + ", z " + (cz << 4) + " ~ " + ((cz << 4) + 15));
                if (current) lore.add("§e▶ 지금 서 있는 곳");

                String name = (current ? "§e★ " : "") + state;
                inventory.setItem(r * 9 + 2 + c, pane(mat, name, lore, current));
            }
        }

        // 범례
        inventory.setItem(0, pane(Material.LIME_STAINED_GLASS_PANE, "§a초록: 구매 가능", new ArrayList<>(), false));
        inventory.setItem(9, pane(Material.RED_STAINED_GLASS_PANE, "§c빨강: 팔린 땅", new ArrayList<>(), false));
        inventory.setItem(18, pane(Material.BLUE_STAINED_GLASS_PANE, "§9파랑: 공용 땅", new ArrayList<>(), false));

        // 정보
        List<String> info = new ArrayList<>();
        int remaining = plugin.remaining();
        info.add(remaining > 0 ? "§7남은 땅: §f" + remaining + " / " + LandPlugin.TOTAL : "§c판매 종료 (남은 땅 없음)");
        info.add("§7지도 위쪽이 북쪽(-Z)입니다");
        info.add("§7/land buy 로 땅 문서를 구매하세요");
        inventory.setItem(17, pane(Material.DIAMOND, "§b현재 가격: 다이아 " + plugin.price() + "개", info, false));
    }

    private static ItemStack pane(Material m, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LandPlugin.text(name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String s : lore) lines.add(LandPlugin.text(s));
            meta.lore(lines);
        }
        if (glint) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(LandPlugin plugin, Player p) {
        p.openInventory(new MapGui(plugin, p).getInventory());
    }

    /** 지도 창에서 아이템을 꺼내거나 옮기지 못하게 막는다. */
    public static final class GuiListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (e.getInventory().getHolder() instanceof MapGui) e.setCancelled(true);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof MapGui) e.setCancelled(true);
        }
    }
}
