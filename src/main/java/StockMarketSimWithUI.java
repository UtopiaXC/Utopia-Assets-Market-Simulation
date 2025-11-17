import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Schedule;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.display.Console;

import sim.util.media.chart.TimeSeriesChartGenerator;
import org.jfree.data.xy.XYSeries;
import javax.swing.JFrame;

/**
 * 启动带 GUI 的模拟器 (已修复 Deprecated 警告)
 */
public class StockMarketSimWithUI extends GUIState {

    public Display2D display;
    public JFrame displayFrame;

    TimeSeriesChartGenerator marketChart;
    XYSeries marketIndexSeries;

    public StockMarketSimWithUI(SimState state) {
        super(state);
    }

    public StockMarketSimWithUI() {
        super(new StockMarketSim(System.currentTimeMillis()));
    }

    public static String getName() {
        return "A股市场 ABM (250天)";
    }

    @Override
    public void start() {
        super.start();
        setupPortrayals();
    }

    @Override
    public void load(SimState state) {
        super.load(state);
        setupPortrayals();
    }

    public void setupPortrayals() {
        StockMarketSim model = (StockMarketSim) state;

        if (marketChart == null) {
            initCharts();
        }

        marketChart.removeAllSeries();
        marketIndexSeries = new XYSeries("市场指数");
        marketChart.addSeries(marketIndexSeries, null);

        // 安排一个匿名 Steppable 来在每一步更新图表
        // 顺序 3，在 Market(2) 之后
        state.schedule.scheduleRepeating(new Steppable() {
            public void step(SimState state) {
                StockMarketSim sim = (StockMarketSim) state;

                if (sim.market.isTradingHours()) {
                    double index = sim.market.marketIndex;
                    double time = sim.schedule.getTime();

                    if (time >= Schedule.EPOCH && time < Schedule.AFTER_SIMULATION) {
                        marketIndexSeries.add(time, index, true);
                    }
                }
            }
        }, 3, 1.0);
    }

    @Override
    public void init(Controller c) {
        super.init(c);

        display = new Display2D(600, 400, this);
        displayFrame = display.createFrame();
        c.registerFrame(displayFrame);
        displayFrame.setVisible(false);

        // 创建图表
        initCharts();
    }

    private void initCharts() {
        marketChart = new TimeSeriesChartGenerator();
        marketChart.setTitle("市场指数 (流通市值加权)");
        marketChart.setYAxisLabel("指数点位");
        marketChart.setXAxisLabel("时间 (步 / 15分钟)");

        // 修正： 'createFrame(this)' 已被弃用
        JFrame chartFrame = marketChart.createFrame();

        chartFrame.pack();

        if (controller != null) {
            controller.registerFrame(chartFrame);
        }
    }

    @Override
    public void quit() {
        super.quit();
        if (displayFrame != null) displayFrame.dispose();
        displayFrame = null;
        display = null;
    }

    public static void main(String[] args) {
        StockMarketSimWithUI vid = new StockMarketSimWithUI();
        Console c = new Console(vid);
        c.setVisible(true);
    }
}