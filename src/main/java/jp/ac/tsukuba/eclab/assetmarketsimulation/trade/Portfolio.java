package jp.ac.tsukuba.eclab.assetmarketsimulation.trade;

import jp.ac.tsukuba.eclab.assetmarketsimulation.market.Stock;
import java.util.HashMap;
import java.util.Map;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.Position;

/**
 * 【【V4.28 修复】】
 * 修复了“现金消失”Bug。
 * 明确跟踪 "cash" (可用) 和 "reservedCash" (挂单)。
 */
public class Portfolio {

    public double cash; // 可用现金
    public double reservedCash; // 挂单冻结的现金
    public Map<Stock, Position> positions;

    public Portfolio(double initialCash) {
        this.cash = initialCash;
        this.reservedCash = 0.0; // 初始为 0
        this.positions = new HashMap<>();
    }

    public double getStockQuantity(Stock stock) {
        Position p = positions.get(stock);
        return (p != null) ? p.totalQuantity : 0.0;
    }

    public double getAvailableQuantity(Stock stock) {
        Position p = positions.get(stock);
        return (p != null) ? p.availableQuantity : 0.0;
    }

    /**
     * 【【V4.28 修改】】
     * 资金从 'cash' 转移到 'reservedCash'。
     */
    public boolean reserveCash(double amount) {
        if (this.cash >= amount) {
            this.cash -= amount;
            this.reservedCash += amount;
            return true;
        }
        return false;
    }

    /**
     * 【【V4.28 新增】】
     * 当买单过期或取消时，将资金从 'reservedCash' 退还到 'cash'。
     */
    public void releaseReservedCash(double amount) {
        if (this.reservedCash >= amount) {
            this.reservedCash -= amount;
            this.cash += amount;
        } else {
            // 这是一个理论上的错误，不应该发生
            System.err.println("Error: Trying to release more reserved cash than available!");
        }
    }


    /**
     * 【【V4.28 修改】】
     * 撮合引擎调用此方法。
     * @param quantity 成交数量
     * @param tradePrice 成交价格
     * @param limitPrice 经纪人挂单的限价 (用于计算退款)
     */
    public boolean addPosition(Stock stock, double quantity, double tradePrice, double limitPrice) {

        double reservedAmount = quantity * limitPrice;
        double actualCost = quantity * tradePrice;

        // 1. 检查保留金是否足够 (理论上应该总是足够的)
        if (this.reservedCash < reservedAmount) {
            System.err.println("CRITICAL: addPosition failed, insufficient reserved cash!");
            // 即使失败了，我们也必须退还经纪人 *部分* 保留金
            // (这种情况很复杂，但 V4.28 的 Market 逻辑避免了这种情况)
            return false;
        }

        // 2. 扣除保留金
        this.reservedCash -= reservedAmount;

        // 3. 计算并退还差价 (如果成交价低于限价)
        double refund = reservedAmount - actualCost;
        if (refund < 0) {
            // (撮合引擎 Bug? 成交价不应高于限价)
            System.err.println("Warning: Trade price higher than limit price. No refund.");
            refund = 0;
        }
        this.cash += refund; // 退还差价

        // 4. 添加股票头寸
        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, 0.0); // T+1
            positions.put(stock, p);
        } else {
            p.totalQuantity += quantity;
        }

        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += actualCost; // (使用实际成交价)
        return true;
    }

    /**
     * 【【V4.25/V4.28 修复】】
     * 修复了资产蒸发 Bug (添加了 else 块)
     */
    public boolean addIPOPosition(Stock stock, double quantity, double price) {

        if (!reserveCash(quantity * price)) {
            System.err.println("IPO Insufficient cash!");
            return false;
        }

        // 现金已被保留。我们现在必须*释放*它 (因为它在 IPO 中立即成交)
        // 并将其转换为股票。
        this.reservedCash -= (quantity * price);

        Position p = positions.get(stock);
        if (p == null) {
            p = new Position(quantity, quantity);
            positions.put(stock, p);
        } else {
            // (V4.24/V4.25 关键修复)
            p.totalQuantity += quantity;
            p.availableQuantity += quantity;
        }
        return true;
    }


    /**
     * (V4.10 - 保持不变)
     */
    public boolean reducePosition(Stock stock, double quantity, double price) {
        Position p = positions.get(stock);
        if (p == null || p.availableQuantity < quantity) {
            return false;
        }
        this.cash += quantity * price; // 卖出时，现金直接增加
        p.totalQuantity -= quantity;
        p.availableQuantity -= quantity;
        if (p.totalQuantity < 0.001) {
            this.positions.remove(stock);
        }
        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += quantity * price;
        return true;
    }

    public double getTotalStockValue() {
        double stockValue = 0;
        for (Map.Entry<Stock, Position> entry : positions.entrySet()) {
            stockValue += entry.getKey().currentPrice * entry.getValue().totalQuantity;
        }
        return stockValue;
    }

    /**
     * 【【V4.28 修复】】
     * 总资产现在是 (可用现金 + 冻结现金 + 股票)
     * (注意: 这个方法在 Portfolio 内部使用，
     * Logger 会单独加总这三项)
     */
    public double getTotalAssets() {
        return this.cash + this.reservedCash + getTotalStockValue();
    }

    public Map<Stock, Position> getPositions() {
        return this.positions;
    }

    public void settleDay() {
        for (Position p : positions.values()) {
            p.availableQuantity = p.totalQuantity;
        }
    }
}