package kr.landclaim;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandCommand implements CommandExecutor, TabCompleter {

    private final LandPlugin plugin;

    public LandCommand(LandPlugin plugin) {
        this.plugin = plugin;
    }

    // ───────────── 실행 ─────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "buy" -> buy(sender);
            case "map" -> map(sender);
            case "price" -> price(sender);
            case "info" -> info(sender);
            case "trust" -> trust(sender, a, true);
            case "untrust" -> trust(sender, a, false);
            case "trusted" -> trustedList(sender);
            case "admin" -> admin(sender, a);
            default -> plugin.msg(sender, "§c알 수 없는 명령어입니다. §f/land help §c를 입력해 보세요.");
        }
        return true;
    }

    private Player playerOnly(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.msg(s, "§c게임 안에서만 사용할 수 있는 명령어입니다.");
        return null;
    }

    // ───────────── 도움말 ─────────────

    private Component line(String cmd, String desc, boolean run) {
        Component c = LandPlugin.text("§e" + cmd + " §7- " + desc);
        return c.clickEvent(run ? ClickEvent.runCommand(cmd) : ClickEvent.suggestCommand(cmd + " "));
    }

    private void help(CommandSender s) {
        s.sendMessage(LandPlugin.text("§6§l──── 땅 시스템 명령어 ────"));
        s.sendMessage(line("/land help", "이 도움말", true));
        s.sendMessage(line("/land map", "5×5 땅 지도 (초록=구매 가능 / 빨강=팔린 땅 / 파랑=공용 땅)", true));
        s.sendMessage(line("/land price", "남은 땅 수와 현재 가격", true));
        s.sendMessage(line("/land buy", "다이아로 개인 땅 문서 구매", true));
        s.sendMessage(line("/land info", "지금 서 있는 청크 정보", true));
        s.sendMessage(line("/land trust <닉네임>", "내 땅에서 건축할 수 있는 친구 추가", false));
        s.sendMessage(line("/land untrust <닉네임>", "친구 허용 해제", false));
        s.sendMessage(line("/land trusted", "허용한 친구 목록", true));
        s.sendMessage(LandPlugin.text("§7땅 문서를 들고 §f아직 팔리지 않은 땅에서 Shift + 우클릭§7하면 그 청크를 구매합니다."));
        if (s.hasPermission("land.admin")) {
            s.sendMessage(LandPlugin.text("§c§l──── 관리자 명령어 ────"));
            s.sendMessage(line("/land admin give public [닉네임]", "공용 땅 문서 지급", false));
            s.sendMessage(line("/land admin give plunder [닉네임]", "땅 약탈 문서 지급", false));
            s.sendMessage(line("/land admin reclaim [청크X 청크Z]", "서 있는(또는 지정한) 청크 회수", false));
            s.sendMessage(line("/land admin reset", "모든 땅과 가격 초기화 (확인 필요)", false));
            s.sendMessage(line("/land admin docs <숫자>", "미사용 문서 수 직접 설정 (분실 문서 정리용)", false));
            s.sendMessage(line("/land admin sold <숫자>", "판매된 문서 수 직접 설정 (가격 조정용)", false));
            s.sendMessage(line("/land admin info", "내부 숫자 확인", true));
        }
    }

    // ───────────── 일반 명령어 ─────────────

    private void price(CommandSender s) {
        int remaining = plugin.remaining();
        if (remaining <= 0) {
            plugin.msg(s, "§c판매가 종료되었습니다. (남은 땅 없음)");
        } else {
            plugin.msg(s, "§a남은 땅: §f" + remaining + " / " + LandPlugin.TOTAL
                    + " §7| §b현재 가격: 다이아 " + plugin.price() + "개");
        }
    }

    private void map(CommandSender s) {
        Player p = playerOnly(s);
        if (p == null) return;
        MapGui.open(plugin, p);
    }

    private void info(CommandSender s) {
        Player p = playerOnly(s);
        if (p == null) return;
        if (!plugin.isLandWorld(p.getWorld())) {
            plugin.msg(p, "§7이 월드에는 땅 시스템이 적용되지 않습니다.");
            return;
        }
        int cx = p.getLocation().getBlockX() >> 4;
        int cz = p.getLocation().getBlockZ() >> 4;
        p.sendMessage(LandPlugin.text(LandPlugin.PREFIX + "§7청크 (" + cx + ", " + cz + ") : ")
                .append(plugin.describe(p, cx, cz)));
    }

    private void buy(CommandSender s) {
        Player p = playerOnly(s);
        if (p == null) return;

        if (plugin.remaining() <= 0) {
            plugin.msg(p, "§c땅 판매가 종료되었습니다. (남은 땅 없음)");
            return;
        }
        int price = plugin.price();
        PlayerInventory inv = p.getInventory();
        ItemStack diamonds = new ItemStack(Material.DIAMOND, price);
        if (!inv.containsAtLeast(new ItemStack(Material.DIAMOND), price)) {
            plugin.msg(p, "§c다이아몬드가 부족합니다. (필요: " + price + "개)");
            return;
        }
        inv.removeItem(diamonds);

        plugin.docsSold++;
        plugin.docsOutstanding++;
        plugin.save();

        Map<Integer, ItemStack> leftover = inv.addItem(plugin.createDoc("personal"));
        for (ItemStack rest : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        plugin.msg(p, "§a개인 땅 문서를 구매했습니다! §7(다이아 " + price + "개 사용)");
        plugin.msg(p, "§7아직 팔리지 않은 땅에서 §fShift + 우클릭§7으로 사용하세요. 다음 가격: 다이아 " + plugin.price() + "개");
    }

    private void trust(CommandSender s, String[] a, boolean add) {
        Player p = playerOnly(s);
        if (p == null) return;
        if (a.length < 2) {
            plugin.msg(p, "§c사용법: /land " + (add ? "trust" : "untrust") + " <닉네임>");
            return;
        }
        UUID target = plugin.resolve(a[1]);
        if (target == null) {
            plugin.msg(p, "§c서버에 접속한 적이 없는 닉네임입니다: " + a[1]);
            return;
        }
        if (target.equals(p.getUniqueId())) {
            plugin.msg(p, "§c자기 자신은 추가할 수 없습니다.");
            return;
        }
        Set<UUID> set = plugin.trusted.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (add) {
            set.add(target);
            plugin.msg(p, "§a" + plugin.nameOf(target) + "님이 내 땅에서 건축할 수 있습니다.");
        } else {
            set.remove(target);
            plugin.msg(p, "§e" + plugin.nameOf(target) + "님의 허용을 해제했습니다.");
        }
        plugin.save();
    }

    private void trustedList(CommandSender s) {
        Player p = playerOnly(s);
        if (p == null) return;
        Set<UUID> set = plugin.trusted.get(p.getUniqueId());
        if (set == null || set.isEmpty()) {
            plugin.msg(p, "§7허용한 친구가 없습니다.");
            return;
        }
        List<String> list = new ArrayList<>();
        for (UUID u : set) list.add(plugin.nameOf(u));
        plugin.msg(p, "§a허용한 친구: §f" + String.join(", ", list));
    }

    // ───────────── 관리자 명령어 ─────────────

    private void admin(CommandSender s, String[] a) {
        if (!s.hasPermission("land.admin")) {
            plugin.msg(s, "§c관리자만 사용할 수 있습니다.");
            return;
        }
        if (a.length < 2) {
            help(s);
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "give" -> adminGive(s, a);
            case "reclaim" -> adminReclaim(s, a);
            case "reset" -> adminReset(s, a);
            case "docs" -> adminNumber(s, a, true);
            case "sold" -> adminNumber(s, a, false);
            case "info" -> {
                plugin.msg(s, "§7판매된 문서 " + plugin.docsSold + "개 / 미사용 문서 " + plugin.docsOutstanding
                        + "개 / 범위 안 팔린 땅 " + plugin.claimedInRange() + "개 / 전체 기록된 땅 " + plugin.claims.size()
                        + "개 / 현재 가격 " + plugin.price() + " / 남은 땅 " + plugin.remaining());
            }
            default -> help(s);
        }
    }

    private void adminGive(CommandSender s, String[] a) {
        if (a.length < 3 || !(a[2].equalsIgnoreCase("public") || a[2].equalsIgnoreCase("plunder"))) {
            plugin.msg(s, "§c사용법: /land admin give <public|plunder> [닉네임]");
            return;
        }
        Player target;
        if (a.length >= 4) {
            target = Bukkit.getPlayerExact(a[3]);
            if (target == null) {
                plugin.msg(s, "§c접속 중인 플레이어가 아닙니다: " + a[3]);
                return;
            }
        } else if (s instanceof Player p) {
            target = p;
        } else {
            plugin.msg(s, "§c닉네임을 지정해 주세요.");
            return;
        }
        String type = a[2].toLowerCase(Locale.ROOT);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(plugin.createDoc(type));
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        plugin.msg(s, "§a" + target.getName() + "님에게 " + (type.equals("public") ? "공용 땅" : "땅 약탈") + " 문서를 지급했습니다.");
    }

    private void adminReclaim(CommandSender s, String[] a) {
        int cx;
        int cz;
        try {
            if (a.length >= 4) {
                cx = Integer.parseInt(a[2]);
                cz = Integer.parseInt(a[3]);
            } else if (s instanceof Player p) {
                cx = p.getLocation().getBlockX() >> 4;
                cz = p.getLocation().getBlockZ() >> 4;
            } else {
                plugin.msg(s, "§c사용법: /land admin reclaim <청크X> <청크Z>");
                return;
            }
        } catch (NumberFormatException ex) {
            plugin.msg(s, "§c청크 좌표는 숫자여야 합니다.");
            return;
        }
        Claim removed = plugin.claims.remove(LandPlugin.key(cx, cz));
        if (removed == null) {
            plugin.msg(s, "§c청크 (" + cx + ", " + cz + ")은(는) 팔린 땅이 아닙니다.");
            return;
        }
        plugin.save();
        String who = removed.isPublic() ? "공용 땅" : removed.ownerName + "님의 땅";
        plugin.msg(s, "§a청크 (" + cx + ", " + cz + ") 회수 완료 (이전: " + who + ")");
    }

    private void adminReset(CommandSender s, String[] a) {
        if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) {
            plugin.msg(s, "§c모든 땅 기록, 판매 횟수, 친구 허용이 삭제됩니다!");
            plugin.msg(s, "§c정말 초기화하려면 §f/land admin reset confirm §c을 입력하세요.");
            return;
        }
        plugin.claims.clear();
        plugin.trusted.clear();
        plugin.docsSold = 0;
        plugin.docsOutstanding = 0;
        plugin.save();
        plugin.msg(s, "§a땅 시스템을 초기화했습니다.");
        Bukkit.broadcast(LandPlugin.text(LandPlugin.PREFIX + "§e땅 시스템이 초기화되었습니다."));
    }

    private void adminNumber(CommandSender s, String[] a, boolean docs) {
        if (a.length < 3) {
            plugin.msg(s, "§c사용법: /land admin " + (docs ? "docs" : "sold") + " <숫자>");
            return;
        }
        int n;
        try {
            n = Math.max(0, Integer.parseInt(a[2]));
        } catch (NumberFormatException ex) {
            plugin.msg(s, "§c숫자를 입력해 주세요.");
            return;
        }
        if (docs) {
            plugin.docsOutstanding = n;
        } else {
            plugin.docsSold = n;
        }
        plugin.save();
        plugin.msg(s, "§a" + (docs ? "미사용 문서 수" : "판매된 문서 수") + "를 " + n + "(으)로 설정했습니다. 현재 가격: 다이아 "
                + plugin.price() + "개");
    }

    // ───────────── 자동완성 ─────────────

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("help", "map", "price", "buy", "info", "trust", "untrust", "trusted"));
            if (s.hasPermission("land.admin")) out.add("admin");
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("trust") || a[0].equalsIgnoreCase("untrust"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("land.admin")) {
            out.addAll(List.of("give", "reclaim", "reset", "docs", "sold", "info"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("give")) {
            out.addAll(List.of("public", "plunder"));
        } else if (a.length == 4 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("admin") && a[1].equalsIgnoreCase("reset")) {
            out.add("confirm");
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
