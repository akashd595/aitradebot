package com.aitrade.aitradebot.repository;

import com.aitrade.aitradebot.entity.StockPriceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockPriceRecordRepository extends JpaRepository<StockPriceRecord, Long> {
    List<StockPriceRecord> findByStockCodeOrderByFetchTimeAsc(String stockCode);

    @org.springframework.transaction.annotation.Transactional
    void deleteByStockCode(String stockCode);
}
