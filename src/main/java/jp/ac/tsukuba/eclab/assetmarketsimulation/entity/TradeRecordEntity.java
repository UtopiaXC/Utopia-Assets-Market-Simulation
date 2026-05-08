package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

/**
 * Trade record entity.
 * influence_json stores the social influence data for this specific trade,
 * avoiding the need for a separate social_influence_log table.
 */
public class TradeRecordEntity {
    private Integer day;
    private Integer stockId;
    private Integer buyerId;
    private Integer sellerId;
    private Double price;
    private Double quantity;

    /**
     * JSON string containing social influence data for this trade.
     * Format: [{"neighborId":123,"similarity":0.85,"belief":1.05,"weight":0.35}, ...]
     * Null if no social influence data is available for this trade.
     */
    private String influenceJson;

    // Computed / join fields
    private String stockCode;
    private String buyerType;
    private String sellerType;

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
    public Integer getStockId() { return stockId; }
    public void setStockId(Integer stockId) { this.stockId = stockId; }
    public Integer getBuyerId() { return buyerId; }
    public void setBuyerId(Integer buyerId) { this.buyerId = buyerId; }
    public Integer getSellerId() { return sellerId; }
    public void setSellerId(Integer sellerId) { this.sellerId = sellerId; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public String getInfluenceJson() { return influenceJson; }
    public void setInfluenceJson(String influenceJson) { this.influenceJson = influenceJson; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getBuyerType() { return buyerType; }
    public void setBuyerType(String buyerType) { this.buyerType = buyerType; }
    public String getSellerType() { return sellerType; }
    public void setSellerType(String sellerType) { this.sellerType = sellerType; }
}
