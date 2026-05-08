package jp.ac.tsukuba.eclab.assetmarketsimulation.trade.model;

import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Market;
import jp.ac.tsukuba.eclab.assetmarketsimulation.scenario.PolicySlot;
import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;
import sim.util.Bag;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 杠杆/配资服务 (Leverage Service)
 *
 * 散户和噪声交易者可以向市场 (机构资金池) 请求配资
 * 当保证金率低于维持保证金时触发强制平仓 → 产生轧空现象 (Short Squeeze)
 */
public class LeverageService {

    private final double marginCallRatio;      // 维持保证金率 (e.g., 1.30)
    private final double dailyInterestRate;    // 日利率

    // 统计
    private int totalMarginCalls = 0;
    private int totalForcedLiquidations = 0;

    public LeverageService() {
        this.marginCallRatio = Config.LEVERAGE_MARGIN_CALL_RATIO;
        this.dailyInterestRate = Config.LEVERAGE_INTEREST_RATE_DAILY;
    }

    /**
     * 请求配资
     * @param borrower 借款人
     * @param maxLeverageRatio 当前政策允许的最大杠杆倍率
     * @return 实际借入的金额 (0 表示未借)
     */
    public double requestLeverage(BaseTrader borrower, double maxLeverageRatio) {
        if (maxLeverageRatio <= 1.0) return 0; // 不允许杠杆

        double netEquity = borrower.portfolio.getNetEquity();
        if (netEquity <= 0) return 0;

        // 当前已借额度
        double currentBorrowed = borrower.portfolio.borrowedCash;
        double totalAssets = borrower.portfolio.getTotalAssets();

        // 最大可借 = netEquity × (maxLeverageRatio - 1) - 已借
        double maxBorrowable = netEquity * (maxLeverageRatio - 1.0) - currentBorrowed;
        if (maxBorrowable <= 0) return 0;

        // 实际借入: 申请净权益的 50% 杠杆
        double requestAmount = netEquity * 0.5;
        double actualBorrow = Math.min(requestAmount, maxBorrowable);

        if (actualBorrow < 1000) return 0; // 太少不借

        borrower.portfolio.cash += actualBorrow;
        borrower.portfolio.borrowedCash += actualBorrow;

        return actualBorrow;
    }

    /**
     * 检查所有有杠杆的交易者的保证金状态
     * 触发强制平仓
     */
    public void checkMarginCalls(Bag traders, Market market) {
        Set<BaseTrader> toForceLiquidate = new HashSet<>();

        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader t)) continue;
            if (!t.isActive()) continue;
            if (t.portfolio.borrowedCash <= 0) continue;

            double marginRatio = t.portfolio.getMarginRatio();

            // 保证金率低于维持线 → 强制平仓
            if (marginRatio < marginCallRatio) {
                toForceLiquidate.add(t);
                totalMarginCalls++;
            }
        }

        // 批量执行强制平仓
        for (BaseTrader t : toForceLiquidate) {
            forcedLiquidation(t, market);
        }
    }

    /**
     * 强制平仓: 以市价卖出所有持仓偿还债务
     * 大量强制平仓同时发生 → 砸盘 → 其他杠杆交易者也被平仓 → 轧空循环 (Short Squeeze)
     */
    private void forcedLiquidation(BaseTrader borrower, Market market) {
        totalForcedLiquidations++;

        System.out.println(">>> [MARGIN CALL] Agent " + borrower.traderId +
                " forced liquidation! Margin: " +
                String.format("%.2f%%", borrower.portfolio.getMarginRatio() * 100) +
                " Borrowed: " + String.format("%.0f", borrower.portfolio.borrowedCash));

        // 以当前价格 -2% 紧急卖出所有可用持仓
        Map<Stock, Position> positions = borrower.portfolio.getPositions();
        for (Map.Entry<Stock, Position> entry : new HashSet<>(positions.entrySet())) {
            Stock stock = entry.getKey();
            double availableQty = entry.getValue().availableQuantity;
            if (availableQty > 0) {
                double panicPrice = stock.currentPrice * 0.98; // 恐慌抛售价
                if (panicPrice < stock.limitDown) panicPrice = stock.limitDown;
                market.submitSellOrder(borrower, stock, availableQty, panicPrice);
            }
        }

        // 用现金偿还部分债务
        double repayment = Math.min(borrower.portfolio.cash, borrower.portfolio.borrowedCash);
        if (repayment > 0) {
            borrower.portfolio.cash -= repayment;
            borrower.portfolio.borrowedCash -= repayment;
        }
    }

    /**
     * 每日利息计费
     */
    public void chargeDailyInterest(Bag traders) {
        for (int i = 0; i < traders.size(); i++) {
            Object obj = traders.get(i);
            if (!(obj instanceof BaseTrader t)) continue;
            if (!t.isActive()) continue;
            if (t.portfolio.borrowedCash <= 0) continue;

            double interest = t.portfolio.borrowedCash * dailyInterestRate;
            t.portfolio.cash -= interest;
            // 利息不增加借款本金，但减少现金
        }
    }

    public int getTotalMarginCalls() {
        return totalMarginCalls;
    }

    public int getTotalForcedLiquidations() {
        return totalForcedLiquidations;
    }
}
