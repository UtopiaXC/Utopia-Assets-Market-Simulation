package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;

@Data
public class HoldingsSnapshotEntity {
    private Integer snapshotDay;
    private Integer agentId;
    /** JSON map: {"stockId": quantity, ...} */
    private String holdingsJson;
}
