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

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;

public class Market implements Steppable {

    // (内部类 Order 和 OrderBook 保持不变)
    private class Order {
        BaseTrader trader;
        Stock stock;
        double quantity;
        double price;
        long timestamp;

        Order(BaseTrader trader, Stock stock, double quantity, double price, long timestamp) {
            this.trader = trader;
            this.stock = stock;
            this.quantity = quantity;
            this.price = price;
            this.timestamp = timestamp;
        }
    }
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

    // --- 指数和市场指标 ---
    public double marketIndex = 3000.0;
    public double indexOpen = 3000.0;
    public double indexHigh = 3000.0;
    public double indexLow = 3000.0;
    public double totalVolumeThisDay = 0;
    public double totalTurnoverThisDay = 0;

    // --- 【【新增 V4.17】】 市场范围指标 ---
    /** 市场总市值 (所有股票，非仅流通) */
    public double marketTotalMarketCap = 0;
    /** 市场振幅 (基于指数) */
    public double marketAmplitude = 0;
    /** 市场换手率 (总成交额 / 总市值) */
    public double marketTurnoverRate = 0;
    // (注意: 量比 (Volume Ratio) 将在 Python 中计算，因为它需要历史数据)
    // --- 【【新增 V4.17】】 结束 ---


    private double initialTotalLiquidMarketCap = 0;

    private StockMarketSim model;

    public Market() {
        this.allOrderBooks = new HashMap<>();
        this.priceHistories = new HashMap<>();

        this.STEPS_PER_DAY = Config.MARKET_STEPS_PER_DAY;
        this.LUNCH_BREAK_START = Config.MARKET_LUNCH_BREAK_START;
        this.LUNCH_BREAK_END = Config.MARKET_LUNCH_BREAK_END;
        this.STEPS_PER_QUARTER = Config.MARKET_STEPS_PER_QUARTER;
        this.INDEX_BASE = Config.MARKET_INDEX_BASE;
    }

    public void setup(StockMarketSim model) {
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

            // 【【新增 V4.17】】
            totalMarketCap += s.totalMarketCap;
        }
        this.initialTotalLiquidMarketCap = (totalCap > 0) ? totalCap : 1.0;

        // 【【新增 V4.17】】
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

            // 【【新增 V4.17】】
            this.marketAmplitude = 0;
            this.marketTurnoverRate = 0;
            // marketTotalMarketCap 在撮合中更新

            for (int i = 0; i < stocksBag.size(); i++) {
                ((Stock) stocksBag.get(i)).resetDailyOHLC();
            }
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
        double currentTotalMarketCap = 0; // 【【新增 V4.17】】

        // (撮合引擎 V4.10 保持不变)
        for (Map.Entry<Stock, OrderBook> entry : allOrderBooks.entrySet()) {
            Stock stock = entry.getKey();
            OrderBook ob = entry.getValue();

            while (true) {
                Order bestBuy = ob.buyOrders.peek();
                Order bestSell = ob.sellOrders.peek();
                if (bestBuy == null || bestSell == null) break;

                if (bestBuy.price >= bestSell.price) {
                    double tradePrice = (bestBuy.timestamp < bestSell.timestamp) ? bestBuy.price : bestBuy.price;
                    double tradeQuantity = Math.min(bestBuy.quantity, bestSell.quantity);

                    boolean buySuccess = bestBuy.trader.portfolio.addPosition(stock, tradeQuantity, tradePrice);
                    boolean sellSuccess = false;

                    if (buySuccess) {
                        sellSuccess = bestSell.trader.portfolio.reducePosition(stock, tradeQuantity, tradePrice);
                        if (!sellSuccess) {
                            bestBuy.trader.portfolio.cash += tradeQuantity * tradePrice;
                            ob.sellOrders.poll();
                            continue;
                        }
                    } else {
                        ob.buyOrders.poll();
                        continue;
                    }

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
            currentTotalMarketCap += stock.totalMarketCap; // 【【新增 V4.17】】
        }

        // (累加成交量逻辑保持不变)
        this.totalVolumeThisDay = 0;
        this.totalTurnoverThisDay = 0;
        for (int i = 0; i < stocksBag.size(); i++) {
            Stock stock = (Stock) stocksBag.get(i);
            this.totalVolumeThisDay += stock.volumeThisDay;
            this.totalTurnoverThisDay += stock.turnoverThisDay;
        }

        // --- 【【修改 V4.17】】 更新市场指标 ---
        this.marketIndex = (currentTotalLiquidMarketCap / this.initialTotalLiquidMarketCap) * INDEX_BASE;
        this.indexHigh = Math.max(this.indexHigh, this.marketIndex);
        this.indexLow = Math.min(this.indexLow, this.marketIndex);

        this.marketTotalMarketCap = currentTotalMarketCap;

        if (this.indexOpen > 0) {
            this.marketAmplitude = (this.indexHigh - this.indexLow) / this.indexOpen;
        }

        if (this.marketTotalMarketCap > 0) {
            // (使用日成交额 / 日总市值 来计算市场换手率)
            this.marketTurnoverRate = this.totalTurnoverThisDay / this.marketTotalMarketCap;
        }
        // --- 【【修改 V4.17】】 结束 ---


        // (收盘逻辑)
        if (currentStep % STEPS_PER_DAY == STEPS_PER_DAY - 1) {

            // 1. 变异 (Mutate)
            for (int i = 0; i < model.traders.size(); i++) {
                ((BaseTrader) model.traders.get(i)).mutateTraits(state);
            }

            // 2. 记录收盘价历史
            for (int i = 0; i < stocksBag.size(); i++) {
                Stock s = (Stock) stocksBag.get(i);
                this.priceHistories.get(s).add(s.currentPrice);
                s.update52WeekHistory(s.currentPrice);
            }

            // 3. T+1 结算
            System.out.println("Market: Executing T+1 end-of-day settlement for Day " + getCurrentDay() + "...");
            for (int i = 0; i < model.traders.size(); i++) {
                ((BaseTrader) model.traders.get(i)).portfolio.settleDay();
            }
        }
    }

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