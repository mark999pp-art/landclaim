package kr.landclaim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandPlugin extends JavaPlugin {

    public static final int RADIUS = 2;                                // 중심 청크에서 양쪽으로 2칸 = 5x5
    public static final int TOTAL = (RADIUS * 2 + 1) * (RADIUS * 2 + 1); // 25
    public static final String PREFIX = "§6[땅] §f";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public final Map<Long, Claim> claims = new HashMap<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    public int docsSold = 0;        // 지금까지 팔린 개인 문서 수 (가격 결정, 줄어들지 않음)
    public int docsOutstanding = 0; // 팔렸지만 아직 사용되지 않은 개인 문서 수

    private File dataFile;
    private NamespacedKey docKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        docKey = new NamespacedKey(this, "doc_type");
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();

        getServer().getPluginManager().registerEvents(new DocListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MapGui.GuiListener(), this);

        LandCommand handler = new LandCommand(this);
        PluginCommand pc = getCommand("land");
        if (pc != null) {
            pc.setExecutor(handler);
            pc.setTabCompleter(handler);
        }
        getLogger().info("LandClaim 활성화 완료. 판매된 문서 " + docsSold + "개, 땅 " + claims.size() + "개");
    }

    @Override
    public void onDisable() {
        save();
    }

    // ───────────── 텍스트 도우미 ─────────────

    public static Component text(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public void msg(CommandSender to, String message) {
        to.sendMessage(text(PREFIX + message));
    }

    // ───────────── 설정 / 좌표 ─────────────

    public String worldName() {
        return getConfig().getString("world", "world");
    }

    public boolean isLandWorld(World w) {
        return w != null && w.getName().equals(worldName());
    }

    public int centerX() {
        return getConfig().getInt("center-chunk-x", 0);
    }

    public int centerZ() {
        return getConfig().getInt("center-chunk-z", 0);
    }

    public boolean inRange(int cx, int cz) {
        return Math.abs(cx - centerX()) <= RADIUS && Math.abs(cz - centerZ()) <= RADIUS;
    }

    public static long key(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    public Claim getClaim(int cx, int cz) {
        return claims.get(key(cx, cz));
    }

    public Claim claimAt(Location loc) {
        if (loc == null || !isLandWorld(loc.getWorld())) return null;
        return claims.get(key(loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
    }

    public boolean isProtected(Location loc) {
        return claimAt(loc) != null;
    }

    // ───────────── 가격 / 남은 땅 ─────────────

    public int price() {
        int base = Math.max(1, getConfig().getInt("price.base", 8));
        int max = Math.max(base, getConfig().getInt("price.max", 128));
        int p = base;
        for (int i = 0; i < docsSold && p < max; i++) {
            p *= 2;
        }
        return Math.min(p, max);
    }

    public int claimedInRange() {
        int n = 0;
        for (Claim c : claims.values()) {
            if (inRange(c.cx, c.cz)) n++;
        }
        return n;
    }

    /** 지금 더 팔 수 있는 개인 땅 수. */
    public int remaining() {
        return Math.max(0, TOTAL - claimedInRange() - docsOutstanding);
    }

    // ───────────── 이름 / 신뢰 ─────────────

    public String nameOf(UUID id) {
        String n = names.get(id);
        if (n != null) return n;
        String off = Bukkit.getOfflinePlayer(id).getName();
        return off != null ? off : "알 수 없음";
    }

    public UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached.getUniqueId() : null;
    }

    public boolean isTrusted(UUID owner, UUID who) {
        Set<UUID> set = trusted.get(owner);
        return set != null && set.contains(who);
    }

    /** 접속 시 이름을 갱신한다 (닉네임을 바꿔도 소유권은 UUID로 유지). */
    public void updateName(Player p) {
        UUID id = p.getUniqueId();
        boolean changed = !p.getName().equals(names.put(id, p.getName()));
        for (Claim c : claims.values()) {
            if (!c.isPublic() && id.equals(c.owner) && !p.getName().equals(c.ownerName)) {
                c.ownerName = p.getName();
                changed = true;
            }
        }
        if (changed) save();
    }

    // ───────────── 땅 문서 아이템 ─────────────

    public ItemStack createDoc(String type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String title;
        List<String> lore = new ArrayList<>();
        switch (type) {
            case "public" -> {
                title = "§9공용 땅 문서";
                lore.add("§7관리자 전용 문서");
                lore.add("§7웅크리고(Shift) 우클릭하면");
                lore.add("§7서 있는 청크가 공용 땅이 됩니다");
            }
            case "plunder" -> {
                title = "§c땅 약탈 문서";
                lore.add("§7관리자 전용 문서");
                lore.add("§7남의 땅에서 웅크리고(Shift) 우클릭하면");
                lore.add("§7그 청크를 빼앗아 내 땅으로 만듭니다");
            }
            default -> {
                type = "personal";
                title = "§a개인 땅 문서";
                lore.add("§7아직 팔리지 않은 땅에서");
                lore.add("§7웅크리고(Shift) 우클릭하면");
                lore.add("§7서 있는 청크(1청크)를 구매합니다");
            }
        }
        meta.displayName(text(title));
        List<Component> loreLines = new ArrayList<>();
        for (String line : lore) loreLines.add(text(line));
        meta.lore(loreLines);
        meta.setEnchantmentGlintOverride(true);
        if (getConfig().getBoolean("use-custom-model", true)) {
            NamespacedKey model = NamespacedKey.fromString("landclaim:" + type);
            if (model != null) meta.setItemModel(model);
        }
        meta.getPersistentDataContainer().set(docKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    public String docType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(docKey, PersistentDataType.STRING);
    }

    // ───────────── 저장 / 불러오기 ─────────────

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("sold", docsSold);
        y.set("outstanding", docsOutstanding);
        for (Claim c : claims.values()) {
            String p = "claims.c" + c.cx + "_" + c.cz;
            y.set(p + ".type", c.type.name());
            if (c.owner != null) y.set(p + ".owner", c.owner.toString());
            y.set(p + ".name", c.ownerName);
        }
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            y.set("names.u" + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Set<UUID>> e : trusted.entrySet()) {
            List<String> list = new ArrayList<>();
            for (UUID u : e.getValue()) list.add(u.toString());
            y.set("trusted.u" + e.getKey(), list);
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("data.yml 저장 실패: " + ex.getMessage());
        }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        docsSold = y.getInt("sold", 0);
        docsOutstanding = y.getInt("outstanding", 0);

        ConfigurationSection cs = y.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                try {
                    String[] parts = k.substring(1).split("_");
                    int cx = Integer.parseInt(parts[0]);
                    int cz = Integer.parseInt(parts[1]);
                    Claim.Type type = Claim.Type.valueOf(cs.getString(k + ".type", "PLAYER"));
                    String ownerStr = cs.getString(k + ".owner");
                    UUID owner = ownerStr == null ? null : UUID.fromString(ownerStr);
                    claims.put(key(cx, cz), new Claim(cx, cz, type, owner, cs.getString(k + ".name", "")));
                } catch (Exception ex) {
                    getLogger().warning("땅 데이터 읽기 실패: " + k);
                }
            }
        }
        ConfigurationSection ns = y.getConfigurationSection("names");
        if (ns != null) {
            for (String k : ns.getKeys(false)) {
                try {
                    names.put(UUID.fromString(k.substring(1)), ns.getString(k, ""));
                } catch (Exception ignored) {
                }
            }
        }
        ConfigurationSection ts = y.getConfigurationSection("trusted");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                try {
                    Set<UUID> set = new HashSet<>();
                    for (String s : ts.getStringList(k)) set.add(UUID.fromString(s));
                    trusted.put(UUID.fromString(k.substring(1)), set);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ───────────── 청크 안내 문구 ─────────────

    public Component describe(Player p, int cx, int cz) {
        Claim c = getClaim(cx, cz);
        if (c == null) {
            if (!inRange(cx, cz)) return text("§7자유 지역 §8(구매 불가)");
            if (remaining() <= 0) return text("§a주인 없는 땅 §8(판매 종료)");
            return text("§a구매 가능 §7(현재 가격 다이아 " + price() + "개)");
        }
        if (c.isPublic()) return text("§9공용 땅");
        if (p != null && p.getUniqueId().equals(c.owner)) return text("§6내 땅");
        return text("§c" + c.ownerName + "님의 땅");
    }
}
