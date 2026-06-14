package com.aitrade.aitradebot.util;

import com.aitrade.aitradebot.entity.Candle;
import java.util.List;

public class TechnicalIndicatorsUtil {

    public static double calculateEMA(List<Candle> candles, int periods, int endIndex) {
        if (candles == null || candles.size() < periods || endIndex < periods - 1) {
            return 0.0;
        }
        
        double multiplier = 2.0 / (periods + 1);
        double ema = candles.get(endIndex - periods + 1).getClosePrice(); // Simple start

        for (int i = endIndex - periods + 2; i <= endIndex; i++) {
            ema = (candles.get(i).getClosePrice() - ema) * multiplier + ema;
        }
        return ema;
    }

    public static double calculateRSI(List<Candle> candles, int periods, int endIndex) {
        if (candles == null || candles.size() <= periods || endIndex < periods) {
            return 0.0;
        }

        double sumGain = 0;
        double sumLoss = 0;

        for (int i = endIndex - periods + 1; i <= endIndex; i++) {
            double change = candles.get(i).getClosePrice() - candles.get(i - 1).getClosePrice();
            if (change > 0) {
                sumGain += change;
            } else {
                sumLoss -= change;
            }
        }

        double avgGain = sumGain / periods;
        double avgLoss = sumLoss / periods;

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public static double calculateDailyVWAP(Candle candle) {
        if (candle == null) return 0.0;
        // Approximation of daily VWAP based on a single daily candle
        return (candle.getHighPrice() + candle.getLowPrice() + candle.getClosePrice()) / 3.0;
    }

    public static double calculateATR(List<Candle> candles, int periods, int endIndex) {
        if (candles == null || candles.size() <= periods || endIndex < periods) {
            return 0.0;
        }

        double sumTr = 0.0;
        for (int i = endIndex - periods + 1; i <= endIndex; i++) {
            sumTr += calculateTrueRange(candles.get(i), candles.get(i - 1));
        }

        double atr = sumTr / periods;
        return atr;
    }

    private static double calculateTrueRange(Candle current, Candle previous) {
        double hl = current.getHighPrice() - current.getLowPrice();
        double hpc = Math.abs(current.getHighPrice() - previous.getClosePrice());
        double lpc = Math.abs(current.getLowPrice() - previous.getClosePrice());
        return Math.max(hl, Math.max(hpc, lpc));
    }
}
