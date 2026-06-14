package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.entity.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PairsTradingEngineService {

    private static final double BETA_HEDGE_RATIO = 1.08;
    private static final double HISTORICAL_MEAN_SPREAD = 12.45;
    private static final double HISTORICAL_STD_DEV = 4.12;

    public Map<String, Object> analyzeAssetPair(List<Candle> historyA, List<Candle> historyB) {
        log.info("Starting Pairs Trading Analysis...");
        
        Map<String, Candle> mapB = historyB.stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleTime().toLocalDate().toString(), 
                        c -> c,
                        (c1, c2) -> c1 
                ));

        List<Candle> alignedA = historyA.stream()
                .filter(cA -> mapB.containsKey(cA.getCandleTime().toLocalDate().toString()))
                .collect(Collectors.toList());

        List<Candle> alignedB = alignedA.stream()
                .map(cA -> mapB.get(cA.getCandleTime().toLocalDate().toString()))
                .collect(Collectors.toList());

        log.info("Aligned candles count: {}. History A original: {}, History B original: {}", 
                 alignedA.size(), historyA.size(), historyB.size());

        Map<String, Object> response = new HashMap<>();

        if (alignedA.size() < 30) {
            log.warn("Insufficient overlapping candles (found {}). Required 30.", alignedA.size());
            response.put("error", "Insufficient overlapping historical data for cointegration.");
            return response;
        }

        Candle latestA = alignedA.get(alignedA.size() - 1);
        Candle latestB = alignedB.get(alignedB.size() - 1);

        double currentPriceA = latestA.getClosePrice();
        double currentPriceB = latestB.getClosePrice();

        double liveSpread = currentPriceA - (BETA_HEDGE_RATIO * currentPriceB);
        double zScore = (liveSpread - HISTORICAL_MEAN_SPREAD) / HISTORICAL_STD_DEV;

        log.info("Pairs Calculation -> Price A: {}, Price B: {}, Live Spread: {}, Z-Score: {}", 
                 currentPriceA, currentPriceB, liveSpread, zScore);

        String decision;
        boolean isSafeToBuy;
        String reasoning;

        if (zScore >= 2.0) {
            decision = "SHORT_A_LONG_B";
            isSafeToBuy = true;
            reasoning = String.format("Z-Score is %.2f (>= 2.0). Spread is historically too wide. Expecting convergence.", zScore);
        } else if (zScore <= -2.0) {
            decision = "LONG_A_SHORT_B";
            isSafeToBuy = true;
            reasoning = String.format("Z-Score is %.2f (<= -2.0). Spread is historically too compressed. Expecting divergence back to mean.", zScore);
        } else {
            decision = "HOLD";
            isSafeToBuy = false;
            reasoning = String.format("Z-Score is %.2f. Spread is within normal historical bounds.", zScore);
        }

        log.info("Pairs Decision: {} - {}", decision, reasoning);

        response.put("currentPriceA", currentPriceA);
        response.put("currentPriceB", currentPriceB);
        response.put("liveSpread", liveSpread);
        response.put("zScore", zScore);
        response.put("decision", decision);
        response.put("isSafeToBuy", isSafeToBuy);
        response.put("reasoning", reasoning);

        return response;
    }
}
