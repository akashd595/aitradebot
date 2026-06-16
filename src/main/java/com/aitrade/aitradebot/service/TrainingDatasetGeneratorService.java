package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.entity.Candle;
import com.aitrade.aitradebot.entity.StockPriceRecord;
import com.aitrade.aitradebot.repository.CandleRepository;
import com.aitrade.aitradebot.repository.StockPriceRecordRepository;
import com.aitrade.aitradebot.util.TechnicalIndicatorsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrainingDatasetGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(TrainingDatasetGeneratorService.class);

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private StockPriceRecordRepository stockPriceRecordRepository;

    @Transactional
    public int generateDataset(String stockCode) {
        logger.info("Starting training dataset generation for stock: {}", stockCode);

        // 1. Delete old stock price records for this stock code
        stockPriceRecordRepository.deleteByStockCode(stockCode);
        logger.debug("Cleared previous price record history logs for {}", stockCode);

        // 2. Fetch all daily candles ordered chronologically
        List<Candle> candles = candleRepository.findByStockCodeOrderByCandleTimeAsc(stockCode);
        logger.info("Loaded {} historical daily candles for dataset creation", candles.size());

        if (candles.size() < 206) { // Need 200 for indicators + 5 for future price + 1 current
            logger.warn("Insufficient candle data size (found {}). Need at least 206 candles (200 history + 5 future + 1 current).", candles.size());
            return 0;
        }

        int recordsSaved = 0;
        // Loop from index 200 to size - 6 to avoid index bounds when checking future price at index i + 5
        for (int i = 200; i < candles.size() - 5; i++) {
            Candle currentCandle = candles.get(i);
            Candle futureCandle = candles.get(i + 5);

            double currentClose = currentCandle.getClosePrice();
            double futureClose = futureCandle.getClosePrice();

            // Calculate target outcome (Target = 1 if future price rose >= 3%, else 0)
            double percentChange = ((futureClose - currentClose) / currentClose) * 100.0;
            int target = percentChange >= 3.0 ? 1 : 0;

            // Calculate technical indicators at index i
            double ema20 = TechnicalIndicatorsUtil.calculateEMA(candles, 20, i);
            double ema200 = TechnicalIndicatorsUtil.calculateEMA(candles, 200, i);
            double rsi = TechnicalIndicatorsUtil.calculateRSI(candles, 14, i);

            // Volume Ratio: Current volume divided by 10-day average volume (from i-9 to i)
            long sumVol = 0;
            int volPeriods = 10;
            for (int j = i - volPeriods + 1; j <= i; j++) {
                sumVol += candles.get(j).getVolume();
            }
            double avgVol = (double) sumVol / volPeriods;
            double volumeRatio = avgVol > 0 ? (double) currentCandle.getVolume() / avgVol : 1.0;

            double ema20Distance = ema20 > 0 ? ((currentClose - ema20) / ema20) * 100.0 : 0.0;
            double ema200Distance = ema200 > 0 ? ((currentClose - ema200) / ema200) * 100.0 : 0.0;

            // Create and persist StockPriceRecord
            StockPriceRecord record = new StockPriceRecord();
            record.setStockCode(stockCode);
            record.setDate(currentCandle.getCandleTime());
            record.setPrice(currentClose);
            record.setRsi(rsi);
            record.setEma20(ema20);
            record.setEma200(ema200);
            record.setVolume(currentCandle.getVolume());
            record.setEma20Distance(ema20Distance);
            record.setEma200Distance(ema200Distance);
            record.setVolumeRatio(volumeRatio);
            record.setTarget(target);

            stockPriceRecordRepository.save(record);
            recordsSaved++;
        }

        logger.info("Successfully generated and saved {} training records for {}", recordsSaved, stockCode);
        return recordsSaved;
    }
}
