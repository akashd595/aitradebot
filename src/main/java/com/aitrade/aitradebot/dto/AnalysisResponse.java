package com.aitrade.aitradebot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResponse {
    private String ticker;
    private String analysisTimestamp;
    private Double currentMarketPrice;
    private Boolean isSafeToBuy;
    private String decision;
    private ExecutionMetrics executionMetrics;
    private String timeBoundExitCondition;
    private String algorithmicReasoning;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionMetrics {
        private RecommendedEntryPriceRange recommendedEntryPriceRange;
        private Double targetPrice;
        private Double stopLossPrice;
        private String riskRewardRatio;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendedEntryPriceRange {
        private Double floor;
        private Double ceiling;
    }
}
