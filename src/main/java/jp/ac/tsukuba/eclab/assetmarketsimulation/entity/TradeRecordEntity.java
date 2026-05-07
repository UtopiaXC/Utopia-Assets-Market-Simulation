package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

/**
 * Compact trade record - amount is NOT stored (= price × quantity, computable).
 */
@Data
public class TradeRecordEntity {
    private Integer day;
    private Integer stockId;
    private Integer buyerId;
    private Integer sellerId;
    private Double price;
    private Double quantity;

    // Computed / join fields
    private String stockCode;
    private String buyerType;
    private String sellerType;
}
