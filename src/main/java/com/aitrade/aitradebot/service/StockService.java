package com.aitrade.aitradebot.service;

import com.aitrade.aitradebot.dto.StockDto;
import com.aitrade.aitradebot.entity.Stock;
import com.aitrade.aitradebot.repository.StockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockService {

    @Autowired
    private StockRepository stockRepository;

    public StockDto createStock(StockDto stockDto) {
        Stock stock = new Stock();
        stock.setStockName(stockDto.getStockName());
        stock.setStockCode(stockDto.getStockCode());

        Stock savedStock = stockRepository.save(stock);

        return mapToDto(savedStock);
    }

    public List<StockDto> getAllStocks() {
        return stockRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public StockDto getStockById(@NonNull Long id) {
        Stock stock = stockRepository.findById(id).orElseThrow(() -> new RuntimeException("Stock not found"));
        return mapToDto(stock);
    }

    public StockDto updateStock(@NonNull Long id, StockDto stockDto) {
        Stock stock = stockRepository.findById(id).orElseThrow(() -> new RuntimeException("Stock not found"));
        stock.setStockName(stockDto.getStockName());
        stock.setStockCode(stockDto.getStockCode());

        Stock updatedStock = stockRepository.save(stock);
        return mapToDto(updatedStock);
    }

    public void deleteStock(@NonNull Long id) {
        stockRepository.deleteById(id);
    }

    private StockDto mapToDto(Stock stock) {
        return new StockDto(stock.getId(), stock.getStockName(), stock.getStockCode());
    }
}
