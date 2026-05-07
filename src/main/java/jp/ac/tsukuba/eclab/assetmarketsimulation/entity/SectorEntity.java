package jp.ac.tsukuba.eclab.assetmarketsimulation.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectorEntity {
    private Integer id;
    private String name;
    private String displayName;
}
