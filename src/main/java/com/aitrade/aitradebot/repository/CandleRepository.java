package com.aitrade.aitradebot.repository;

import com.aitrade.aitradebot.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findByStockCodeOrderByCandleTimeAsc(String stockCode);

    @Query("SELECT c FROM Candle c WHERE c.stockCode = :stockCode AND c.closePrice > c.openPrice")
    List<Candle> findBullishCandles(@Param("stockCode") String stockCode);

    Optional<Candle> findFirstByStockCodeOrderByCandleTimeDesc(String stockCode);

    void deleteByStockCode(String stockCode);
}
