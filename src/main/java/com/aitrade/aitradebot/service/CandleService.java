package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.dto.LatestCandleResponse;
import com.aitrade.aitradebot.entity.Candle;
import com.aitrade.aitradebot.entity.StockPriceRecord;
import com.aitrade.aitradebot.repository.CandleRepository;
import com.aitrade.aitradebot.repository.StockPriceRecordRepository;
import com.aitrade.aitradebot.util.TechnicalIndicatorsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CandleService {

    private static final Logger logger = LoggerFactory.getLogger(CandleService.class);

    @Autowired
    private CandleRepository candleRepository;

    @Autowired
    private StockPriceRecordRepository stockPriceRecordRepository;

    @Autowired
    private AiInferenceEngine aiInferenceEngine;

    public Candle saveCandle(@NonNull Candle candle) {
        return candleRepository.save(candle);
    }

    public List<Candle> getCandlesByStockCode(String stockCode) {
        return candleRepository.findByStockCodeOrderByCandleTimeAsc(stockCode);
    }

    public List<StockPriceRecord> getPriceHistory(String stockCode) {
        return stockPriceRecordRepository.findByStockCodeOrderByFetchTimeAsc(stockCode);
    }

    public List<Candle> getBullishCandlesByStockCode(String stockCode) {
        return candleRepository.findBullishCandles(stockCode);
    }

    public LatestCandleResponse getLatestCandle(String stockCode) {
        Candle latest = candleRepository.findFirstByStockCodeOrderByCandleTimeDesc(stockCode)
                .orElse(null);
        if (latest == null) {
            return null;
        }
        
        BigDecimal close = BigDecimal.valueOf(latest.getClosePrice());
        BigDecimal open = BigDecimal.valueOf(latest.getOpenPrice());
        BigDecimal priceMovement = close.subtract(open);
        
        return new LatestCandleResponse(latest, priceMovement);
    }

    @Transactional
    public void generateMockData(String stockCode) {
        // Now fetches real live data from Yahoo Finance!
        String yahooSymbol = stockCode;
        if (!yahooSymbol.contains(".")) {
            yahooSymbol += ".NS"; // Default to NSE
        }
        
        logger.info("Attempting to fetch live market data for symbol: {}", yahooSymbol);
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + yahooSymbol + "?interval=1d&range=2y";
            logger.debug("Requesting Yahoo Finance URL: {}", url);
            
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();
            
            logger.info("=====================================================");
            logger.info("[ONLINE DATA FETCH TRACE]");
            logger.info("Method/File : CandleService.java -> generateMockData()");
            logger.info("Time        : {}", LocalDateTime.now());
            logger.info("Target URL  : {}", url);
            logger.info("=====================================================");
            
            // Log the raw JSON data that was fetched
            logger.debug("Raw JSON Response from Yahoo Finance:\n{}", response);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode resultNode = root.path("chart").path("result").get(0);
            
            if (resultNode == null || resultNode.isMissingNode()) {
                logger.warn("No data found in Yahoo Finance response for symbol: {}", yahooSymbol);
                return;
            }
            
            JsonNode timestamps = resultNode.path("timestamp");
            JsonNode quoteNode = resultNode.path("indicators").path("quote").get(0);
            
            if (timestamps == null || quoteNode == null) {
                logger.error("Invalid JSON structure received from Yahoo Finance for {}", yahooSymbol);
                return;
            }

            JsonNode opens = quoteNode.path("open");
            JsonNode closes = quoteNode.path("close");
            JsonNode highs = quoteNode.path("high");
            JsonNode lows = quoteNode.path("low");
            JsonNode volumes = quoteNode.path("volume");
            
            // Delete old dummy data
            logger.debug("Deleting old data for stockCode: {}", stockCode);
            candleRepository.deleteByStockCode(stockCode);
            
            int count = 0;
            List<Candle> tempCandles = new java.util.ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (opens.get(i).isNull() || closes.get(i).isNull() || highs.get(i).isNull() || lows.get(i).isNull()) continue; // Skip null/incomplete entries
                
                long timestamp = timestamps.get(i).asLong();
                double open = opens.get(i).asDouble();
                double close = closes.get(i).asDouble();
                double high = highs.get(i).asDouble();
                double low = lows.get(i).asDouble();
                long volume = volumes.get(i).asLong();
                
                LocalDateTime candleTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
                
                Candle candle = new Candle();
                candle.setStockCode(stockCode);
                candle.setTimeFrame("1D");
                candle.setOpenPrice(open);
                candle.setClosePrice(close);
                candle.setHighPrice(high);
                candle.setLowPrice(low);
                candle.setVolume(volume);
                candle.setCandleTime(candleTime);
                
                candleRepository.save(candle);
                tempCandles.add(candle);
                count++;
            }
            
            if (!tempCandles.isEmpty()) {
                Candle latestSavedCandle = tempCandles.get(tempCandles.size() - 1);
                int lastIndex = tempCandles.size() - 1;
                
                double currentPrice = latestSavedCandle.getClosePrice();
                double ema20 = 0.0;
                double ema200 = 0.0;
                double rsi = 0.0;
                
                if (tempCandles.size() >= 20) {
                    ema20 = TechnicalIndicatorsUtil.calculateEMA(tempCandles, 20, lastIndex);
                }
                if (tempCandles.size() >= 200) {
                    ema200 = TechnicalIndicatorsUtil.calculateEMA(tempCandles, 200, lastIndex);
                }
                if (tempCandles.size() > 14) {
                    rsi = TechnicalIndicatorsUtil.calculateRSI(tempCandles, 14, lastIndex);
                }
                
                // Volume indicators
                long latestVol = latestSavedCandle.getVolume();
                long sumVol = 0;
                int volPeriods = Math.min(10, tempCandles.size());
                for (int j = lastIndex - volPeriods + 1; j <= lastIndex; j++) {
                    sumVol += tempCandles.get(j).getVolume();
                }
                double avgVol = volPeriods > 0 ? (double) sumVol / volPeriods : 0.0;
                double volumeRatio = avgVol > 0 ? (double) latestVol / avgVol : 1.0;
                
                double ema20Distance = ema20 > 0 ? ((currentPrice - ema20) / ema20) * 100.0 : 0.0;
                double ema200Distance = ema200 > 0 ? ((currentPrice - ema200) / ema200) * 100.0 : 0.0;
                
                // Run Trading Analysis Filters to compute Target
                double vwap = TechnicalIndicatorsUtil.calculateDailyVWAP(latestSavedCandle);
                boolean isAbove200Ema = currentPrice > ema200;
                boolean isRsiValid = rsi >= 42.0 && rsi <= 55.0;
                boolean isWithinEma20Band = currentPrice >= ema20 * 0.98 && currentPrice <= ema20 * 1.02;
                boolean isAboveVwap = currentPrice > vwap;
                
                boolean isSafeToBuy = isAbove200Ema && isRsiValid && isWithinEma20Band && isAboveVwap && (tempCandles.size() >= 200);
                
                if (isSafeToBuy) {
                    float rsiFeature = (float) rsi;
                    float distEma20 = (float) ((currentPrice - ema20) / ema20);
                    float distEma200 = (float) ((currentPrice - ema200) / ema200);
                    float volDelta = avgVol > 0 ? (float) ((latestVol - avgVol) / avgVol) : 0f;
                    
                    boolean aiPass = aiInferenceEngine.confirmSignal(rsiFeature, distEma20, distEma200, volDelta);
                    isSafeToBuy = aiPass;
                }
                
                int targetVal = isSafeToBuy ? 1 : 0;
                
                StockPriceRecord record = new StockPriceRecord();
                record.setStockCode(stockCode);
                record.setDate(latestSavedCandle.getCandleTime());
                record.setPrice(currentPrice);
                record.setVolume(latestVol);
                record.setEma20(ema20);
                record.setEma200(ema200);
                record.setRsi(rsi);
                record.setEma20Distance(ema20Distance);
                record.setEma200Distance(ema200Distance);
                record.setVolumeRatio(volumeRatio);
                record.setTarget(targetVal);
                
                stockPriceRecordRepository.save(record);
                logger.info("Saved stock price record snapshot for {}: Date={}, Price={}, RSI={}, EMA20_DISTANCE={}, EMA200_DISTANCE={}, VOLUME_RATIO={}, TARGET={}", 
                        stockCode, record.getDate(), record.getPrice(), record.getRsi(), record.getEma20Distance(), record.getEma200Distance(), record.getVolumeRatio(), record.getTarget());
            }
            
            logger.info("Successfully fetched and saved {} real daily candles for {}", count, stockCode);
        } catch (Exception e) {
            logger.error("Failed to fetch real data from Yahoo Finance for {}. Reason: {}", stockCode, e.getMessage(), e);
        }
    }
}
