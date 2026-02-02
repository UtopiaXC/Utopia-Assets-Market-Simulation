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
import java.util.Iterator;

// 本项目
import jp.ac.tsukuba.eclab.assetmarketsimulation.Config;
import jp.ac.tsukuba.eclab.assetmarketsimulation.StockMarketSim;
import jp.ac.tsukuba.eclab.assetmarketsimulation.trade.trader.BaseTrader;

public class Market implements Steppable {

    private class Order {
        BaseTrader trader;
        Stock stock;
        double quantity;
        double price; // Limit Price
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
                        .thenComparingLong(o -> o.timestamp));
        PriorityQueue<Order> sellOrders = new PriorityQueue<>(
                Comparator.comparingDouble((Order o) -> o.price)
                        .thenComparingLong(o -> o.timestamp));
    }

    private Map<Stock, OrderBook> allOrderBooks;
    private Map<Stock, ArrayList<Double>> priceHistories;

    // 【新增 V4.33】 市场指数历史，用于计算 FOMO
    private ArrayList<Double> marketIndexHistory = new ArrayList<>();

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

    // 订单有效期 (3 天)
    private final long ORDER_EXPIRY_STEPS;

    public Market() {
        this(Config.MARKET_STEPS_PER_DAY);
    }

    public Market(int stepsPerDay) {
        this.allOrderBooks = new HashMap<>();
        this.priceHistories = new HashMap<>();
        this.marketIndexHistory = new ArrayList<>();

        this.STEPS_PER_DAY = stepsPerDay;

        if (stepsPerDay == Config.MARKET_STEPS_PER_DAY) {
            this.LUNCH_BREAK_START = Config.MARKET_LUNCH_BREAK_START;
            this.LUNCH_BREAK_END = Config.MARKET_LUNCH_BREAK_END;
            this.STEPS_PER_QUARTER = Config.MARKET_STEPS_PER_QUARTER;
        } else {
            // Custom steps implies continuous trading (no lunch break simulation)
            // TODO: In the future, if we want to simulate idle steps (e.g. for news
            // propagation during lunch),
            // we can calculate LUNCH_BREAK_START/END here based on time mapping.
            // Currently we skip lunch steps to focus on trading efficiency.
            this.LUNCH_BREAK_START = stepsPerDay; // Always trading
            this.LUNCH_BREAK_END = stepsPerDay;
            // Scale quarter steps
            this.STEPS_PER_QUARTER = (int) (Config.MARKET_STEPS_PER_QUARTER
                    * ((double) stepsPerDay / Config.MARKET_STEPS_PER_DAY));
        }

        this.INDEX_BASE = Config.MARKET_INDEX_BASE;

        this.ORDER_EXPIRY_STEPS = (long) 3 * this.STEPS_PER_DAY;
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
            totalMarketCap += s.totalMarketCap;
        }
        this.initialTotalLiquidMarketCap = (totalCap > 0) ? totalCap : 1.0;
        this.marketTotalMarketCap = totalMarketCap;
        this.marketIndex = INDEX_BASE;
        this.indexOpen = INDEX_BASE;
        this.indexHigh = INDEX_BASE;
        this.indexLow = INDEX_BASE;

        // 初始化指数历史
        this.marketIndexHistory.add(INDEX_BASE);
    }

    public int getCurrentDay() {
        return (int) (currentStep / STEPS_PER_DAY) + 1;
    }

    public boolean isTradingHours() {
        long stepInDay = currentStep % STEPS_PER_DAY;
        return (stepInDay < LUNCH_BREAK_START) || (stepInDay >= LUNCH_BREAK_END);
    }

    public synchronized void submitBuyOrder(BaseTrader trader, Stock stock, double quantity, double price) {
        if (!isTradingHours())
            return;
        if (price > stock.limitUp || price < stock.limitDown) {
            return;
        }
        OrderBook ob = allOrderBooks.get(stock);
        if (ob != null) {
            ob.buyOrders.add(new Order(trader, stock, quantity, price, currentStep));
        }
    }

    public synchronized void submitSellOrder(BaseTrader trader, Stock stock, double quantity, double price) {
        if (!isTradingHours())
            return;
        if (price > stock.limitUp || price < stock.limitDown) {
            return;
        }
        OrderBook ob = allOrderBooks.get(stock);
        if (ob != null) {
            ob.sellOrders.add(new Order(trader, stock, quantity, price, currentStep));
        }
    }

    private void pruneExpiredOrders() {
        if (ORDER_EXPIRY_STEPS <= 0)
            return;

        for (OrderBook ob : allOrderBooks.values()) {
            Iterator<Order> buyIter = ob.buyOrders.iterator();
            while (buyIter.hasNext()) {
                Order order = buyIter.next();
                if ((currentStep - order.timestamp) > ORDER_EXPIRY_STEPS) {
                    order.trader.portfolio.releaseReservedCash(order.quantity * order.price);
                    buyIter.remove();
                }
            }
            Iterator<Order> sellIter = ob.sellOrders.iterator();
            while (sellIter.hasNext()) {
                Order order = sellIter.next();
                if ((currentStep - order.timestamp) > ORDER_EXPIRY_STEPS) {
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
                Stock s = (Stock) stocksBag.get(i);
                s.updateLimits();
                s.resetDailyOHLC();
            }

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

        for (Map.Entry<Stock, OrderBook> entry : allOrderBooks.entrySet()) {
            Stock stock = entry.getKey();
            OrderBook ob = entry.getValue();

            while (true) {
                Order bestBuy = ob.buyOrders.peek();
                Order bestSell = ob.sellOrders.peek();
                if (bestBuy == null || bestSell == null)
                    break;

                if (bestBuy.price >= bestSell.price) {
                    double tradePrice = (bestBuy.timestamp < bestSell.timestamp) ? bestBuy.price : bestSell.price;

                    if (tradePrice > stock.limitUp)
                        tradePrice = stock.limitUp;
                    if (tradePrice < stock.limitDown)
                        tradePrice = stock.limitDown;

                    double tradeQuantity = Math.min(bestBuy.quantity, bestSell.quantity);

                    double sellerAvailable = bestSell.trader.portfolio.getAvailableQuantity(stock);
                    if (sellerAvailable < tradeQuantity - 0.000001) {
                        ob.sellOrders.poll();
                        continue;
                    }

                    boolean sellSuccess = bestSell.trader.portfolio.reducePosition(stock, tradeQuantity, tradePrice);

                    if (sellSuccess) {
                        boolean buySuccess = bestBuy.trader.portfolio.addPosition(
                                stock, tradeQuantity, tradePrice, bestBuy.price);

                        if (!buySuccess) {
                            System.err.println("CRITICAL: Buy execution failed.");
                            bestSell.trader.portfolio.cash -= (tradeQuantity * tradePrice);
                            if (bestSell.trader.portfolio.cash < 0)
                                bestSell.trader.portfolio.cash = 0;
                            ob.buyOrders.poll();
                            continue;
                        }
                    } else {
                        ob.sellOrders.poll();
                        continue;
                    }

                    stock.currentPrice = tradePrice;
                    stock.high = Math.max(stock.high, tradePrice);
                    stock.low = Math.min(stock.low, tradePrice);

                    bestBuy.quantity -= tradeQuantity;
                    bestSell.quantity -= tradeQuantity;

                    if (bestBuy.quantity < 0.001)
                        ob.buyOrders.poll();
                    if (bestSell.quantity < 0.001)
                        ob.sellOrders.poll();

                } else {
                    break;
                }
            }

            stock.updateDerivedData();
            currentTotalLiquidMarketCap += stock.liquidMarketCap;
            currentTotalMarketCap += stock.totalMarketCap;
        }

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

        if (currentStep % STEPS_PER_DAY == STEPS_PER_DAY - 1) {
            // 记录指数历史
            this.marketIndexHistory.add(this.marketIndex);

            for (int i = 0; i < model.traders.size(); i++) {
                Object obj = model.traders.get(i);
                if (obj instanceof BaseTrader) {
                    ((BaseTrader) obj).mutateTraits(state);
                }
            }
            for (int i = 0; i < stocksBag.size(); i++) {
                Stock s = (Stock) stocksBag.get(i);
                this.priceHistories.get(s).add(s.currentPrice);
                s.update52WeekHistory(s.currentPrice);
            }
            System.out.println("Market: Executing T+1 end-of-day settlement for Day " + getCurrentDay() + "...");
            for (int i = 0; i < model.traders.size(); i++) {
                Object obj = model.traders.get(i);
                if (obj instanceof BaseTrader) {
                    ((BaseTrader) obj).portfolio.settleDay();
                }
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

    // 【新增 V4.33】 获取近期市场回报率
    public double getRecentReturn(int lookbackDays) {
        if (marketIndexHistory.isEmpty())
            return 0.0;
        int currentIdx = marketIndexHistory.size() - 1;
        int pastIdx = Math.max(0, currentIdx - lookbackDays);

        double currentVal = marketIndexHistory.get(currentIdx);
        double pastVal = marketIndexHistory.get(pastIdx);

        if (pastVal <= 0)
            return 0.0;
        return (currentVal - pastVal) / pastVal;
    }
}