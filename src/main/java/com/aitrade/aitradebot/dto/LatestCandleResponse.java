package com.aitrade.aitradebot.dto;

import com.aitrade.aitradebot.entity.Candle;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LatestCandleResponse {
    private Candle latestCandle;
    private BigDecimal priceMovement;
}
