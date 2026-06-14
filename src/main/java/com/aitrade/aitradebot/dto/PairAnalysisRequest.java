package com.aitrade.aitradebot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PairAnalysisRequest {
    private String tickerA;
    private String tickerB;
}
