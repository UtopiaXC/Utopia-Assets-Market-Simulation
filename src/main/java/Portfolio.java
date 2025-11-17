import java.util.HashMap;
import java.util.Map;

public class Portfolio {

    public double cash;
    public Map<Stock, Double> positions;

    public Portfolio(double initialCash) {
        this.cash = initialCash;
        this.positions = new HashMap<>();
    }

    public double getStockQuantity(Stock stock) {
        return positions.getOrDefault(stock, 0.0);
    }

    public void addPosition(Stock stock, double quantity, double price) {
        if (cash < quantity * price) {
            // (现金不足的检查已在 Trader 中完成，这里再加一层保险)
            System.err.println(Thread.currentThread().getName() + " 现金不足!");
            return;
        }
        this.cash -= quantity * price;
        this.positions.put(stock, getStockQuantity(stock) + quantity);

        // 修正：更新 Stock 的 *每日* 成交量/额
        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += quantity * price;
    }

    public void reducePosition(Stock stock, double quantity, double price) {
        double currentQuantity = getStockQuantity(stock);
        if (currentQuantity < quantity) {
            System.err.println(Thread.currentThread().getName() + " 股票不足!");
            return;
        }
        this.cash += quantity * price;
        double newQuantity = currentQuantity - quantity;

        if (newQuantity < 0.001) {
            this.positions.remove(stock);
        } else {
            this.positions.put(stock, newQuantity);
        }

        // 修正：更新 Stock 的 *每日* 成交量/额
        stock.volumeThisDay += quantity;
        stock.turnoverThisDay += quantity * price;
    }

    public double getTotalStockValue() {
        double stockValue = 0;
        for (Map.Entry<Stock, Double> entry : positions.entrySet()) {
            stockValue += entry.getKey().currentPrice * entry.getValue();
        }
        return stockValue;
    }

    public double getTotalAssets() {
        return this.cash + getTotalStockValue();
    }

    // 新增：返回持仓详情 (用于 DatabaseLogger)
    public Map<Stock, Double> getPositions() {
        return this.positions;
    }
}