package com.aitrade.aitradebot.controller;

import com.aitrade.aitradebot.dto.AnalysisRequest;
import com.aitrade.aitradebot.dto.AnalysisResponse;
import com.aitrade.aitradebot.dto.PairAnalysisRequest;
import com.aitrade.aitradebot.service.CandleService;
import com.aitrade.aitradebot.service.PairsTradingEngineService;
import com.aitrade.aitradebot.service.TradingEngineService;
import com.aitrade.aitradebot.entity.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trading")
public class TradingController {

    @Autowired
    private TradingEngineService tradingEngineService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeStock(@RequestBody AnalysisRequest request) {
        if (request == null || request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new IllegalArgumentException("Ticker symbol is required in the request payload.");
        }
        
        AnalysisResponse response = tradingEngineService.analyze(request);
        return ResponseEntity.ok(response);
    }

    @Autowired
    private PairsTradingEngineService pairsTradingEngineService;

    @Autowired
    private CandleService candleService;

    @PostMapping("/analyze-pair")
    public ResponseEntity<Map<String, Object>> analyzePair(@RequestBody PairAnalysisRequest request) {
        if (request == null || request.getTickerA() == null || request.getTickerB() == null) {
            throw new IllegalArgumentException("Both tickerA and tickerB are required.");
        }
        
        // Ensure data exists/is fetched
        candleService.generateMockData(request.getTickerA());
        candleService.generateMockData(request.getTickerB());
        
        List<Candle> historyA = candleService.getCandlesByStockCode(request.getTickerA());
        List<Candle> historyB = candleService.getCandlesByStockCode(request.getTickerB());
        
        Map<String, Object> response = pairsTradingEngineService.analyzeAssetPair(historyA, historyB);
        return ResponseEntity.ok(response);
    }
}
