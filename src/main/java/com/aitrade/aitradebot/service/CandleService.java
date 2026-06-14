package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.dto.LatestCandleResponse;
import com.aitrade.aitradebot.entity.Candle;
import com.aitrade.aitradebot.repository.CandleRepository;
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

    public Candle saveCandle(@NonNull Candle candle) {
        return candleRepository.save(candle);
    }

    public List<Candle> getCandlesByStockCode(String stockCode) {
        return candleRepository.findByStockCodeOrderByCandleTimeAsc(stockCode);
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
            for (int i = 0; i < timestamps.size(); i++) {
                if (opens.get(i).isNull()) continue; // Skip null entries
                
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
                count++;
            }
            
            logger.info("Successfully fetched and saved {} real daily candles for {}", count, stockCode);
        } catch (Exception e) {
            logger.error("Failed to fetch real data from Yahoo Finance for {}. Reason: {}", stockCode, e.getMessage(), e);
        }
    }
}
