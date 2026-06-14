package com.aitrade.aitradebot.controller;

import com.aitrade.aitradebot.dto.StockDto;
import com.aitrade.aitradebot.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StockController {

    @Autowired
    private StockService stockService;

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Trading backend running");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stocks")
    public ResponseEntity<StockDto> createStock(@RequestBody StockDto stockDto) {
        StockDto createdStock = stockService.createStock(stockDto);
        return new ResponseEntity<>(createdStock, HttpStatus.CREATED);
    }

    @GetMapping("/stocks")
    public ResponseEntity<List<StockDto>> getAllStocks() {
        List<StockDto> stocks = stockService.getAllStocks();
        return ResponseEntity.ok(stocks);
    }

    @GetMapping("/stocks/{id}")
    public ResponseEntity<StockDto> getStockById(@PathVariable Long id) {
        StockDto stock = stockService.getStockById(id);
        return ResponseEntity.ok(stock);
    }

    @PutMapping("/stocks/{id}")
    public ResponseEntity<StockDto> updateStock(@PathVariable Long id, @RequestBody StockDto stockDto) {
        StockDto updatedStock = stockService.updateStock(id, stockDto);
        return ResponseEntity.ok(updatedStock);
    }

    @DeleteMapping("/stocks/{id}")
    public ResponseEntity<String> deleteStock(@PathVariable Long id) {
        stockService.deleteStock(id);
        return ResponseEntity.ok("Stock deleted successfully");
    }
}
