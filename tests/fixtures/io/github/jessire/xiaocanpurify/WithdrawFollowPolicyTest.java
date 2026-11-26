package io.github.jessire.xiaocanpurify;

import java.util.ArrayList;
import java.util.List;
import io.github.jessire.xiaocanpurify.WithdrawFollowPolicy.Node;

public final class WithdrawFollowPolicyTest {
    public static void main(String[] args) {
        var fixture = List.of(new Node(2,1,"提现明细",null,false), new Node(3,1,"提现成功",null,false),
                new Node(11,10,"下次如何更快知道？",null,false),
                new Node(12,10,"关注小蚕霸王餐，不错过每一笔收益！",null,false),
                new Node(13,10,"新人红包\n订单提醒\n秒杀预告",null,false),
                new Node(14,10,"立即关注",null,true), new Node(15,10,null,"关闭",true));
        eq(fixture, 10, 1, 15);
        for (int i=0; i<fixture.size(); i++) {
            var missing = new ArrayList<>(fixture); missing.remove(i); eq(missing,10,1,-1);
        }
        var ambiguous = new ArrayList<>(fixture); ambiguous.add(new Node(16,10,"关闭",null,true)); eq(ambiguous,10,1,-1);
        eq(fixture,10,4,-1); eq(fixture,8,1,-1);
        var unrelatedClose = new ArrayList<>(fixture); unrelatedClose.set(6,new Node(15,1,"关闭",null,true)); eq(unrelatedClose,10,1,-1);
        var paintedClose = new ArrayList<>(fixture); paintedClose.set(6,new Node(15,10,"X",null,false)); eq(paintedClose,10,1,-1);
        var falseClose = new ArrayList<>(fixture); falseClose.set(6,new Node(15,10,"立即关注","关闭",true)); eq(falseClose,10,1,-1);
        System.out.println("PASS: withdraw follow promotion (14 cases)");
    }
    private static void eq(List<Node> nodes, int top, int result, int want) {
        int got = WithdrawFollowPolicy.closeId(nodes, top, result);
        if (got != want) throw new AssertionError("wanted " + want + ", got " + got);
    }
}
