package io.github.vgnri.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ポートフォリオ管理クラス
 */
public class Portfolio implements Serializable {
    
    /**
     * 個別銘柄のポートフォリオエントリ
     */
    public static class Entry implements Serializable {
        private int stockId;
        private String stockName;
        private int totalQuantity;
        private List<Acquisition> acquisitions = new ArrayList<>();

        public Entry(int stockId, String stockName) {
            this.stockId = stockId;
            this.stockName = stockName;
        }

        public void addAcquisition(double price, int quantity) {
            acquisitions.add(new Acquisition(price, quantity));
            totalQuantity += quantity;
        }

        public double getAverageCost() {
            int total = 0;
            double sum = 0.0;
            for (Acquisition acq : acquisitions) {
                sum += acq.price * acq.quantity;
                total += acq.quantity;
            }
            return total > 0 ? sum / total : 0.0;
        }

        // Getters
        public int getTotalQuantity() {
            return totalQuantity;
        }

        public String getStockName() {
            return stockName;
        }

        public int getStockId() {
            return stockId;
        }

        public List<Acquisition> getAcquisitions() {
            return acquisitions;
        }
    }

    /**
     * 取得情報（価格と数量）
     */
    public static class Acquisition implements Serializable {
        public double price;
        public int quantity;

        public Acquisition(double price, int quantity) {
            this.price = price;
            this.quantity = quantity;
        }
    }

    // 株主ID -> (銘柄ID -> ポートフォリオエントリ)
    private final Map<Integer, Portfolio.Entry> holdings = new ConcurrentHashMap<>();
    private int shareholderId;

    public Portfolio(int shareholderId) {
        this.shareholderId = shareholderId;
    }

    /**
     * ポートフォリオに取引を追加
     */
    public void addTransaction(int stockId, String stockName, int quantity, double price) {
        holdings.computeIfAbsent(stockId, k -> new Portfolio.Entry(stockId, stockName))
                .addAcquisition(price, quantity);
    }

    /**
     * 保有銘柄のマップを取得
     */
    public Map<Integer, Portfolio.Entry> getHoldings() {
        return holdings;
    }

    /**
     * 株主IDを取得
     */
    public int getShareholderId() {
        return shareholderId;
    }

    /**
     * ポートフォリオが空かどうか
     */
    public boolean isEmpty() {
        return holdings.isEmpty();
    }
}
