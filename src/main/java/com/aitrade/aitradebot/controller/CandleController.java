package com.aitrade.aitradebot.controller;

import com.aitrade.aitradebot.dto.LatestCandleResponse;
import com.aitrade.aitradebot.entity.Candle;
import com.aitrade.aitradebot.entity.StockPriceRecord;
import com.aitrade.aitradebot.service.CandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candles")
public class CandleController {

    @Autowired
    private CandleService candleService;

    @PostMapping
    public ResponseEntity<Candle> createCandle(@RequestBody Candle candle) {
        Candle savedCandle = candleService.saveCandle(candle);
        return new ResponseEntity<>(savedCandle, HttpStatus.CREATED);
    }

    @GetMapping("/{stockCode}")
    public ResponseEntity<List<Candle>> getCandlesByStockCode(@PathVariable String stockCode) {
        List<Candle> candles = candleService.getCandlesByStockCode(stockCode);
        return new ResponseEntity<>(candles, HttpStatus.OK);
    }

    @GetMapping("/bullish/{stockCode}")
    public ResponseEntity<List<Candle>> getBullishCandlesByStockCode(@PathVariable String stockCode) {
        List<Candle> bullishCandles = candleService.getBullishCandlesByStockCode(stockCode);
        return new ResponseEntity<>(bullishCandles, HttpStatus.OK);
    }

    @GetMapping("/latest/{stockCode}")
    public ResponseEntity<LatestCandleResponse> getLatestCandle(@PathVariable String stockCode) {
        LatestCandleResponse response = candleService.getLatestCandle(stockCode);
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/price-history/{stockCode}")
    public ResponseEntity<List<StockPriceRecord>> getPriceHistory(@PathVariable String stockCode) {
        List<StockPriceRecord> history = candleService.getPriceHistory(stockCode);
        return new ResponseEntity<>(history, HttpStatus.OK);
    }
}
