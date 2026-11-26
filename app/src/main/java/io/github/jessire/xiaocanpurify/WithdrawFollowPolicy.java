package io.github.jessire.xiaocanpurify;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WithdrawFollowPolicy {
    private WithdrawFollowPolicy() {}
    record Node(int id, int route, String label, String tooltip, boolean tap) {}

    static int closeId(List<Node> nodes, int topRoute, int resultRoute) {
        if (topRoute <= 0 || resultRoute < 0 || resultRoute == topRoute) return -1;
        int copy = 0;
        int close = -1;
        int follow = -1;
        Set<Integer> details = new HashSet<>(), success = new HashSet<>();
        for (Node node : nodes) {
            String label = normalize(node.label());
            String tooltip = normalize(node.tooltip());
            if (node.route() != topRoute) {
                if (node.route() != resultRoute) continue;
                if (label.equals("提现明细")) details.add(node.route());
                if (label.equals("提现成功")) success.add(node.route());
                continue;
            }
            for (String part : label.split("\n")) {
                switch (part.trim()) {
                    case "下次如何更快知道?", "下次如何更快知道？" -> copy |= 1;
                    case "关注小蚕霸王餐，不错过每一笔收益", "关注小蚕霸王餐，不错过每一笔收益！",
                         "关注小蚕霸王餐,不错过每一笔收益", "关注小蚕霸王餐,不错过每一笔收益!" -> copy |= 2;
                    case "新人红包" -> copy |= 4;
                    case "订单提醒" -> copy |= 8;
                    case "秒杀预告" -> copy |= 16;
                    case "新人红包、订单提醒、秒杀预告" -> copy |= 28;
                    default -> { }
                }
            }
            if (!node.tap()) continue;
            if (label.equals("立即关注")) {
                if (follow != -1) return -1;
                follow = node.id();
            }
            if ((isClose(label) && (tooltip.isEmpty() || isClose(tooltip)))
                    || (label.isEmpty() && isClose(tooltip))) {
                if (close != -1) return -1;
                close = node.id();
            }
        }
        details.retainAll(success);
        return details.size() == 1 && copy == 31 && follow > 0 && close > 0 && close != follow ? close : -1;
    }

    private static boolean isClose(String s) {
        return s.equals("关闭") || s.equals("关闭弹窗") || s.equals("×") || s.equals("X") || s.equals("x");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace('\r', '\n').replace('\u3000', ' ').trim();
    }
}
