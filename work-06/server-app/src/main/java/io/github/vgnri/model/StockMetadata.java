package io.github.vgnri.model;

// 株メタデータクラス
public class StockMetadata {
    private final int stockId;
    private final String stockName;
    private final int dividend;
    private final String dividendDate;
    private final long capital;
    private final int employees;
    private final String companySize;
    private final String market;
    
    // constructor, getters
    public StockMetadata(int stockId, String stockName, int dividend, String dividendDate, 
                        long capital, int employees, String companySize, String market) {
        this.stockId = stockId;
        this.stockName = stockName;
        this.dividend = dividend;
        this.dividendDate = dividendDate;
        this.capital = capital;
        this.employees = employees;
        this.companySize = companySize;
        this.market = market;
    }

    public int getStockId() {
        return stockId;
    }

    public String getStockName() {
        return stockName;
    }

    public int getDividend() {
        return dividend;
    }

    public String getDividendDate() {
        return dividendDate;
    }

    public long getCapital() {
        return capital;
    }

    public int getEmployees() {
        return employees;
    }

    public String getCompanySize() {
        return companySize;
    }

    public String getMarket() {
        return market;
    }
    
    @Override
    public String toString() {
        return String.format("StockMetadata{id=%d, name='%s', market='%s'}", 
                           stockId, stockName, market);
    }
}
