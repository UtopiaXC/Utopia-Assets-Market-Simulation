package jp.ac.tsukuba.eclab.assetmarketsimulation.dto;

import java.util.List;

/**
 * Sector statistics data
 */
public class SectorStatsDTO {

    // Market cap by sector over time
    public List<SectorData> marketCapHistory;

    // PE ratio by sector over time
    public List<SectorData> peHistory;

    /**
     * Sector data point
     */
    public static class SectorData {
        public int day;
        public String sector;
        public double value;

        public SectorData() {
        }

        public SectorData(int day, String sector, double value) {
            this.day = day;
            this.sector = sector;
            this.value = value;
        }
    }
}
