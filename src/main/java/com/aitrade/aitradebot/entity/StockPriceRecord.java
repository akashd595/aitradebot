package com.aitrade.aitradebot.entity;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_price_record")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockPriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode;
    private LocalDateTime date; // Represents the trading day date of the price data
    private Double price;       // Latest close price
    private Double rsi;         // 14-period RSI
    private Double ema20;       // 20-day EMA
    private Double ema200;      // 200-day EMA
    private Long volume;        // Latest trading volume

    private Double ema20Distance; // (Price - EMA20) / EMA20 * 100
    private Double ema200Distance; // (Price - EMA200) / EMA200 * 100
    private Double volumeRatio;   // Latest Volume / 10-day Average Volume
    private Integer target;       // 1 if BUY, 0 if HOLD

    @CreationTimestamp
    private LocalDateTime fetchTime;
}
