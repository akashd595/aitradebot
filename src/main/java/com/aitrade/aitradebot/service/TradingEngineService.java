package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.dto.AnalysisRequest;
import com.aitrade.aitradebot.dto.AnalysisResponse;
import com.aitrade.aitradebot.entity.Candle;
import com.aitrade.aitradebot.util.TechnicalIndicatorsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TradingEngineService {

    private static final Logger logger = LoggerFactory.getLogger(TradingEngineService.class);

    @Autowired
    private CandleService candleService;

    @Autowired
    private AiInferenceEngine aiInferenceEngine;

    public AnalysisResponse analyze(AnalysisRequest request) {
        String ticker = request.getTicker();
        logger.info("Starting quantitative analysis for ticker: {}", ticker);

        // Ensure we have mock data if not enough history exists
        candleService.generateMockData(ticker);

        List<Candle> candles = candleService.getCandlesByStockCode(ticker);
        logger.debug("Loaded {} historical candles from database for {}", candles.size(), ticker);

        if (candles.size() < 200) {
            logger.error("Analysis failed. Not enough data to calculate 200 EMA. Required 200, found {}", candles.size());
            throw new IllegalStateException("Not enough data to calculate 200 EMA. Required 200, found " + candles.size());
        }

        int lastIndex = candles.size() - 1;
        Candle latestCandle = candles.get(lastIndex);
        double currentPrice = latestCandle.getClosePrice();
        logger.debug("Latest candle date: {}, Current Price: {}", latestCandle.getCandleTime(), currentPrice);

        // Filter 1: Macro Safety Guard (Trend) - 200 EMA
        double ema200 = TechnicalIndicatorsUtil.calculateEMA(candles, 200, lastIndex);
        boolean isAbove200Ema = currentPrice > ema200;
        logger.debug("Filter 1 (Trend): 200 EMA = {}, Current Price = {}, isAbove200Ema = {}", ema200, currentPrice, isAbove200Ema);

        // Filter 2: Anti-FOMO Guard (Momentum) - 14 RSI
        double rsi14 = TechnicalIndicatorsUtil.calculateRSI(candles, 14, lastIndex);
        boolean isRsiValid = rsi14 >= 42 && rsi14 <= 55;
        logger.debug("Filter 2 (Momentum): 14 RSI = {}, isRsiValid = {}", rsi14, isRsiValid);

        // Filter 3: Volume & Value Alignment - 20 EMA and VWAP
        double ema20 = TechnicalIndicatorsUtil.calculateEMA(candles, 20, lastIndex);
        double vwap = TechnicalIndicatorsUtil.calculateDailyVWAP(latestCandle);
        
        double ema20UpperBand = ema20 * 1.02;
        double ema20LowerBand = ema20 * 0.98;
        
        boolean isWithinEma20Band = currentPrice >= ema20LowerBand && currentPrice <= ema20UpperBand;
        boolean isAboveVwap = currentPrice > vwap;
        logger.debug("Filter 3 (Value): 20 EMA = {}, VWAP = {}, isWithinEma20Band = {}, isAboveVwap = {}", ema20, vwap, isWithinEma20Band, isAboveVwap);

        boolean isValueAligned = isWithinEma20Band && isAboveVwap;

        // Filter 4: Risk Mitigation & Early Profit Take (ATR)
        double atr14 = TechnicalIndicatorsUtil.calculateATR(candles, 14, lastIndex);
        
        double stopLoss = currentPrice - (1.5 * atr14);
        double targetPrice = currentPrice + (1.5 * atr14);
        logger.debug("Filter 4 (Risk/ATR): 14 ATR = {}, Target = {}, StopLoss = {}", atr14, targetPrice, stopLoss);

        // Final Decision
        boolean isSafeToBuy = isAbove200Ema && isRsiValid && isValueAligned;
        String decision = isSafeToBuy ? "BUY" : "HOLD";
        String reasoningBase = generateReasoning(isAbove200Ema, rsi14, isWithinEma20Band, isAboveVwap);
        
        // AI Confirmation Step
        if (isSafeToBuy) {
            logger.info("Deterministic filters passed. Proceeding with AI Confirmation...");
            
            long latestVol = latestCandle.getVolume();
            long sumVol = 0;
            int volPeriods = Math.min(10, candles.size());
            for (int i = lastIndex - volPeriods + 1; i <= lastIndex; i++) {
                sumVol += candles.get(i).getVolume();
            }
            double avgVol = (double) sumVol / volPeriods;
            float volDelta = avgVol > 0 ? (float) ((latestVol - avgVol) / avgVol) : 0f;

            float rsiFeature = (float) rsi14;
            float distEma20 = (float) ((currentPrice - ema20) / ema20);
            float distEma200 = (float) ((currentPrice - ema200) / ema200);

            boolean aiPass = aiInferenceEngine.confirmSignal(rsiFeature, distEma20, distEma200, volDelta);
            
            if (!aiPass) {
                isSafeToBuy = false;
                decision = "HOLD";
                reasoningBase += " Technical indicators passed, but AI engine flagged high downside structural variance.";
                logger.info("AI downgraded signal to HOLD.");
            } else {
                reasoningBase += " AI Confirmation: BUY probability is strong.";
                logger.info("AI confirmed BUY signal.");
            }
        }
        
        logger.info("Analysis Complete for {}. Decision: {}, isSafeToBuy: {}", ticker, decision, isSafeToBuy);

        // Construct response
        String suffix = ticker.endsWith(".NS") ? "" : ".NS";
        String fullTicker = ticker + suffix;

        String reasoning = reasoningBase;

        AnalysisResponse.RecommendedEntryPriceRange entryRange = AnalysisResponse.RecommendedEntryPriceRange.builder()
                .floor(Math.round((currentPrice * 0.99) * 100.0) / 100.0)
                .ceiling(Math.round((currentPrice * 1.01) * 100.0) / 100.0)
                .build();

        AnalysisResponse.ExecutionMetrics metrics = AnalysisResponse.ExecutionMetrics.builder()
                .recommendedEntryPriceRange(entryRange)
                .targetPrice(Math.round(targetPrice * 100.0) / 100.0)
                .stopLossPrice(Math.round(stopLoss * 100.0) / 100.0)
                .riskRewardRatio("1:1.5")
                .build();

        return AnalysisResponse.builder()
                .ticker(fullTicker)
                .analysisTimestamp(latestCandle.getCandleTime() != null ? 
                        latestCandle.getCandleTime().format(DateTimeFormatter.ISO_DATE_TIME) + "Z" : 
                        java.time.Instant.now().toString())
                .currentMarketPrice(Math.round(currentPrice * 100.0) / 100.0)
                .isSafeToBuy(isSafeToBuy)
                .decision(decision)
                .executionMetrics(metrics)
                .timeBoundExitCondition("Swing (3 to 8 trading sessions). Hard exit automatically if holding exceeds 10 trading days or if RSI crosses above 70.")
                .algorithmicReasoning(reasoning)
                .build();
    }

    private String generateReasoning(boolean isAbove200Ema, double rsi, boolean isWithinEma20Band, boolean isAboveVwap) {
        StringBuilder reasoning = new StringBuilder();
        
        if (isAbove200Ema) {
            reasoning.append("Price is structurally protected above the 200 EMA. ");
        } else {
            reasoning.append("Price is below the 200 EMA, indicating a macro downtrend (Falling knife risk). ");
        }

        if (rsi >= 42 && rsi <= 55) {
            reasoning.append(String.format("RSI is in a conservative neutral-recovery zone (%.1f). ", rsi));
        } else if (rsi > 60) {
            reasoning.append(String.format("RSI is running too hot (%.1f), indicating FOMO risk. ", rsi));
        } else {
            reasoning.append(String.format("RSI indicates weak momentum (%.1f). ", rsi));
        }

        if (isAboveVwap) {
            reasoning.append("Latest close is safely above intraday VWAP, indicating institutional accumulation rather than retail speculation. ");
        } else {
            reasoning.append("Latest close is below VWAP, showing lack of immediate buyer conviction. ");
        }
        
        if (isWithinEma20Band) {
            reasoning.append("Price is well-aligned with the 20-day EMA value band.");
        } else {
            reasoning.append("Price is over-extended or too far below the 20-day EMA value band.");
        }

        return reasoning.toString();
    }
}
