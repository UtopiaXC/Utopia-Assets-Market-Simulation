package jp.ac.tsukuba.eclab.assetmarketsimulation.service;

import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO;
import jp.ac.tsukuba.eclab.assetmarketsimulation.dto.MarketOverviewDTO.*;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.MarketDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.entity.StockDailyEntity;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.MarketDailyMapper;
import jp.ac.tsukuba.eclab.assetmarketsimulation.mapper.StockDailyMapper;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Market analysis service - uses MyBatis mappers
 */
@Service
public class MarketAnalysisService {

    @Autowired
    private DatabaseService databaseService;

    // Cache for completed simulations
    private final java.util.Map<String, CachedKline> klineCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedKline {
        final long lastModified;
        final List<MarketDayData> data;
        CachedKline(long lastModified, List<MarketDayData> data) {
            this.lastModified = lastModified;
            this.data = data;
        }
    }

    public List<MarketDayData> getKlineData(String dbFile) {
        java.io.File file = new java.io.File(dbFile);
        if (!file.exists()) file = new java.io.File("output", dbFile);
        long currentMod = file.exists() ? file.lastModified() : 0;

        CachedKline cached = klineCache.get(file.getAbsolutePath());
        if (cached != null && cached.lastModified == currentMod && currentMod > 0) {
            return cached.data;
        }

        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            List<MarketDayData> kline = toKlineData(marketMapper.selectAll());
            if (currentMod > 0) {
                klineCache.put(file.getAbsolutePath(), new CachedKline(currentMod, kline));
            }
            return kline;
        }
    }

    public MarketOverviewDTO getMarketOverview(String dbFile, int day) {
        MarketOverviewDTO result = new MarketOverviewDTO();
        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper marketMapper = session.getMapper(MarketDailyMapper.class);
            StockDailyMapper stockMapper = session.getMapper(StockDailyMapper.class);

            result.klineData = getKlineData(dbFile);
            MarketDailyEntity dayEntity = marketMapper.selectByDay(day);
            result.dayDetail = toDayDetail(dayEntity);
            result.topActiveStocks = toTopStocks(stockMapper.selectTopByTurnover(day, 10));
        }
        return result;
    }

    public int getTotalDays(String dbFile) {
        try (SqlSession session = databaseService.openSession(dbFile)) {
            MarketDailyMapper mapper = session.getMapper(MarketDailyMapper.class);
            Integer maxDay = mapper.selectMaxDay();
            return maxDay != null ? maxDay : 0;
        }
    }

    private List<MarketDayData> toKlineData(List<MarketDailyEntity> entities) {
        List<MarketDayData> data = new ArrayList<>();
        for (MarketDailyEntity e : entities) {
            data.add(new MarketDayData(e.getDay(), e.getOpen(), e.getHigh(), e.getLow(), e.getClose(),
                    e.getVolume(), e.getTurnover(), e.getTotalMarketCap(),
                    e.getAmplitude(), e.getTurnoverRate(), e.getSocialWealthPool()));
        }
        return data;
    }

    private MarketDayDetail toDayDetail(MarketDailyEntity e) {
        if (e == null) return null;
        return new MarketDayDetail(e.getDay(), e.getClose(), e.getVolume(), e.getTurnover(),
                e.getTurnoverRate(), e.getSocialWealthPool());
    }

    private List<TopStock> toTopStocks(List<StockDailyEntity> entities) {
        List<TopStock> stocks = new ArrayList<>();
        int rank = 1;
        for (StockDailyEntity e : entities) {
            stocks.add(new TopStock(rank++,
                    e.getStockCode() != null ? e.getStockCode() : String.valueOf(e.getStockId()),
                    e.getSectorName() != null ? e.getSectorName() : "",
                    e.getClose(), e.getTurnover(), e.getVolume(), e.getTurnoverRate()));
        }
        return stocks;
    }
}
