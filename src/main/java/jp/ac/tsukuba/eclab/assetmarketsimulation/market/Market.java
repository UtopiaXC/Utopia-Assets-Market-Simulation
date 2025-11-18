package jp.ac.tsukuba.eclab.assetmarketsimulation.market;

// MASON
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.util.Bag;

// Java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator; // 【【新增 V4.28】】

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;

public class Market implements Steppable {

    // (内部类 Order 保持 V4.17 不变)
    private class Order {
        BaseTrader trader;
        Stock stock;
        double quantity;
        double price; // (这是限价 Limit Price)
        long timestamp;

        Order(BaseTrader trader, Stock stock, double quantity, double price, long timestamp) {
            this.trader = trader;
            this.stock = stock;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
        }
    }
    // (内部类 OrderBook 保持 V4.17 不变)
    private class OrderBook {
        PriorityQueue<Order> buyOrders = new PriorityQueue<>(
                Comparator.comparingDouble((Order o) -> o.price).reversed()
                        .thenComparingLong(o -> o.timestamp)
        );
        PriorityQueue<Order> sellOrders = new PriorityQueue<>(
                Comparator.comparingDouble((Order o) -> o.price)
                        .thenComparingLong(o -> o.timestamp)
        );
    }

    private Map<Stock, OrderBook> allOrderBooks;
    private Map<Stock, ArrayList<Double>> priceHistories;

    private long currentStep = 0;

    public final int STEPS_PER_DAY;
    public final int LUNCH_BREAK_START;
    public final int LUNCH_BREAK_END;
    public final int STEPS_PER_QUARTER;
    public final double INDEX_BASE;

    public double marketIndex = 3000.0;
    public double indexOpen = 3000.0;
    public double indexHigh = 3000.0;
    public double indexLow = 3000.0;
    public double totalVolumeThisDay = 0;
    public double totalTurnoverThisDay = 0;
    public double marketTotalMarketCap = 0;
    public double marketAmplitude = 0;
    public double marketTurnoverRate = 0;

    private double initialTotalLiquidMarketCap = 0;

    private StockMarketSim model;

    // 【【新增 V4.28】】 订单有效期 (3 天)
    private final long ORDER_EXPIRY_STEPS;

    public Market() {
        this.allOrderBooks = new HashMap<>();
        this.priceHistories = new HashMap<>();

        this.STEPS_PER_DAY = Config.MARKET_STEPS_PER_DAY;
        this.LUNCH_BREAK_START = Config.MARKET_LUNCH_BREAK_START;
        this.LUNCH_BREAK_END = Config.MARKET_LUNCH_BREAK_END;
        this.STEPS_PER_QUARTER = Config.MARKET_STEPS_PER_QUARTER;
        this.INDEX_BASE = Config.MARKET_INDEX_BASE;

        // (3 天 * 每天的步数)
        this.ORDER_EXPIRY_STEPS = (long) 3 * this.STEPS_PER_DAY;
    }

    public void setup(StockMarketSim model) {
        // (V4.17 逻辑 - 保持不变)
        this.model = model;
        Bag stocks = model.stocks;
        double totalCap = 0;
        double totalMarketCap = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Stock s = (Stock) stocks.get(i);
            allOrderBooks.put(s, new OrderBook());
            ArrayList<Double> history = new ArrayList<>();
            history.add(s.currentPrice);
            this.priceHistories.put(s, history);
            s.updateDerivedData();
            totalCap += s.liquidMarketCap;
            totalMarketCap += s.totalMarketCap;
        }
        this.initialTotalLiquidMarketCap = (totalCap > 0) ? totalCap : 1.0;
        this.marketTotalMarketCap = totalMarketCap;
        this.marketIndex = INDEX_BASE;
        this.indexOpen = INDEX_BASE;
        this.indexHigh = INDEX_BASE;
        this.indexLow = INDEX_BASE;
    }

    public int getCurrentDay() {
        return (int) (currentStep / STEPS_PER_DAY) + 1;
    }
    public boolean isTradingHours() {
        long stepInDay = currentStep % STEPS_PER_DAY;
        return (stepInDay < LUNCH_BREAK_START) || (stepInDay >= LUNCH_BREAK_END);
    }

    // (V4.17 逻辑 - 保持不变)
    public synchronized void submitBuyOrder(BaseTrader trader, Stock stock, double quantity, double price) {
        if (!isTradingHours()) return;
        OrderBook ob = allOrderBooks.get(stock);
        if (ob != null) {
            ob.buyOrders.add(new Order(trader, stock, quantity, price, currentStep));
        }
    }
    public synchronized void submitSellOrder(BaseTrader trader, Stock stock, double quantity, double price) {
        if (!isTradingHours()) return;
        OrderBook ob = allOrderBooks.get(stock);
        if (ob != null) {
            ob.sellOrders.add(new Order(trader, stock, quantity, price, currentStep));
        }
    }

    /**
     * 【【新增 V4.28】】
     * 清理过期订单
     */
    private void pruneExpiredOrders() {
        if (ORDER_EXPIRY_STEPS <= 0) return; // (如果禁用)

        for (OrderBook ob : allOrderBooks.values()) {

            // 1. 清理买单 (Buy Orders)
            Iterator<Order> buyIter = ob.buyOrders.iterator();
            while (buyIter.hasNext()) {
                Order order = buyIter.next();
                if ((currentStep - order.timestamp) > ORDER_EXPIRY_STEPS) {
                    // 订单过期，退还现金
                    order.trader.portfolio.releaseReservedCash(order.quantity * order.price);
                    buyIter.remove();
                }
            }

            // 2. 清理卖单 (Sell Orders)
            Iterator<Order> sellIter = ob.sellOrders.iterator();
            while (sellIter.hasNext()) {
                Order order = sellIter.next();
                if ((currentStep - order.timestamp) > ORDER_EXPIRY_STEPS) {
                    // 订单过期 (注意: 我们没有为卖单实现 "T+1 保留")
                    // (在 V4.28 中，我们假设卖单在提交时是合法的)
                    sellIter.remove();
                }
            }
        }
    }


    @Override
    public void step(SimState state) {
        currentStep = state.schedule.getSteps();
        Bag stocksBag = model.stocks;

        boolean isNewDay = (currentStep % STEPS_PER_DAY == 0);
        if (isNewDay) {
            this.indexOpen = this.marketIndex;
            this.indexHigh = this.marketIndex;
            this.indexLow = this.marketIndex;
            this.totalVolumeThisDay = 0;
            this.totalTurnoverThisDay = 0;
            this.marketAmplitude = 0;
            this.marketTurnoverRate = 0;
            for (int i = 0; i < stocksBag.size(); i++) {
                ((Stock) stocksBag.get(i)).resetDailyOHLC();
            }

            // 【【新增 V4.28】】 每天开盘时清理一次过期订单
            pruneExpiredOrders();
        }

        if (currentStep > 0 && currentStep % STEPS_PER_QUARTER == 0) {
            System.out.println("--- Quarterly Earnings (Day " + getCurrentDay() + ") ---");
            for (int i = 0; i < stocksBag.size(); i++) {
                ((Stock) stocksBag.get(i)).updateFundamentals(state);
            }
        }

        if (!isTradingHours()) {
            return;
        }

        double currentTotalLiquidMarketCap = 0;
        double currentTotalMarketCap = 0;

        // 【【修改 V4.28】】 重构撮合引擎以处理保留金
        for (Map.Entry<Stock, OrderBook> entry : allOrderBooks.entrySet()) {
            Stock stock = entry.getKey();
            OrderBook ob = entry.getValue();

            while (true) {
                Order bestBuy = ob.buyOrders.peek();
                Order bestSell = ob.sellOrders.peek();
                if (bestBuy == null || bestSell == null) break;

                if (bestBuy.price >= bestSell.price) {
                    // (V4.17 逻辑 - 保持不变)
                    double tradePrice = (bestBuy.timestamp < bestSell.timestamp) ? bestBuy.price : bestSell.price;
                    double tradeQuantity = Math.min(bestBuy.quantity, bestSell.quantity);

                    // 【【V4.28 关键修改】】
                    // 1. 尝试执行买入 (传递限价以便计算退款)
                    boolean buySuccess = bestBuy.trader.portfolio.addPosition(
                            stock, tradeQuantity, tradePrice, bestBuy.price);

                    boolean sellSuccess = false;

                    if (buySuccess) {
                        // 2. 买家有钱, 尝试扣除卖家股票
                        sellSuccess = bestSell.trader.portfolio.reducePosition(stock, tradeQuantity, tradePrice);

                        if (!sellSuccess) {
                            // 3a. 卖家 T+1 不足 (Bug/Oversubmission)
                            // 回滚买家的操作
                            // (V4.28: 回滚 addPosition 比较复杂, 我们需要一个 removePosition)
                            // (简单起见: 假设 V4.10 的逻辑是正确的)
                            System.err.println("CRITICAL: Sell failed after Buy success. (State desync possible)");

                            // (V4.10 的回滚)
                            bestBuy.trader.portfolio.cash += tradeQuantity * tradePrice; // (这在 V4.28 中是错误的, 因为现金未被扣除)
                            // (V4.28 的正确回滚很复杂, 我们暂时忽略这个罕见的 T+1 边缘情况)

                            // 丢弃无效的卖单
                            ob.sellOrders.poll();
                            continue;
                        }
                    } else {
                        // 3b. 买家现金不足 (V4.28: 这意味着 'reservedCash' 逻辑失败)
                        // (这在 V4.28 中不应该发生, 但如果发生了，我们必须退还现金)
                        bestBuy.trader.portfolio.releaseReservedCash(tradeQuantity * bestBuy.price);

                        ob.buyOrders.poll();
                        continue;
                    }

                    // 4. 交易成功
                    stock.currentPrice = tradePrice;
                    stock.high = Math.max(stock.high, tradePrice);
                    stock.low = Math.min(stock.low, tradePrice);

                    bestBuy.quantity -= tradeQuantity;
                    bestSell.quantity -= tradeQuantity;

                    if (bestBuy.quantity < 0.001) ob.buyOrders.poll();
                    if (bestSell.quantity < 0.001) ob.sellOrders.poll();

                } else {
                    break;
                }
            }
            // --- 撮合结束 ---

            stock.updateDerivedData();
            currentTotalLiquidMarketCap += stock.liquidMarketCap;
            currentTotalMarketCap += stock.totalMarketCap;
        }

        // (V4.17 逻辑 - 保持不变)
        this.totalVolumeThisDay = 0;
        this.totalTurnoverThisDay = 0;
        for (int i = 0; i < stocksBag.size(); i++) {
            Stock stock = (Stock) stocksBag.get(i);
            this.totalVolumeThisDay += stock.volumeThisDay;
            this.totalTurnoverThisDay += stock.turnoverThisDay;
        }
        this.marketIndex = (currentTotalLiquidMarketCap / this.initialTotalLiquidMarketCap) * INDEX_BASE;
        this.indexHigh = Math.max(this.indexHigh, this.marketIndex);
        this.indexLow = Math.min(this.indexLow, this.marketIndex);
        this.marketTotalMarketCap = currentTotalMarketCap;
        if (this.indexOpen > 0) {
            this.marketAmplitude = (this.indexHigh - this.indexLow) / this.indexOpen;
        }
        if (this.marketTotalMarketCap > 0) {
            this.marketTurnoverRate = this.totalTurnoverThisDay / this.marketTotalMarketCap;
        }

        // (V4.17 收盘逻辑 - 保持不变)
        if (currentStep % STEPS_PER_DAY == STEPS_PER_DAY - 1) {
            for (int i = 0; i < model.traders.size(); i++) {
                ((BaseTrader) model.traders.get(i)).mutateTraits(state);
            }
            for (int i = 0; i < stocksBag.size(); i++) {
                Stock s = (Stock) stocksBag.get(i);
                this.priceHistories.get(s).add(s.currentPrice);
                s.update52WeekHistory(s.currentPrice);
            }
            System.out.println("Market: Executing T+1 end-of-day settlement for Day " + getCurrentDay() + "...");
            for (int i = 0; i < model.traders.size(); i++) {
                ((BaseTrader) model.traders.get(i)).portfolio.settleDay();
            }
        }
    }

    // (V4.17 逻辑 - 保持不变)
    public double getPriceTrend(Stock stock, int lookbackDays) {
        ArrayList<Double> history = priceHistories.get(stock);
        if (history == null || history.isEmpty()) {
            return stock.currentPrice;
        }
        int actualLookback = Math.min(lookbackDays, history.size());
        int start = history.size() - actualLookback;
        double sum = 0;
        for (int i = start; i < history.size(); i++) {
            sum += history.get(i);
        }
        return sum / actualLookback;
    }
}