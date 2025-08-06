package io.github.vgnri.model;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;

public class StockInfo {
    // enum定義
    public enum CompanyType {
        LARGE("大"),
        MEDIUM("中"), 
        SMALL("小");
        
        private final String displayName;
        
        CompanyType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static CompanyType fromString(String text) {
            for (CompanyType type : CompanyType.values()) {
                if (type.displayName.equals(text)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("不正な会社タイプ: " + text);
        }
    }
    
    public enum MarketType {
        JAPAN("日"),
        USA("米"),
        EUROPE("欧");
        
        private final String displayName;
        
        MarketType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static MarketType fromString(String text) {
            for (MarketType type : MarketType.values()) {
                if (type.displayName.equals(text)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("不正な市場タイプ: " + text);
        }
    }
    
    private int stockId;
    private String stockName;
    private int dividendPerShare;
    private MonthDay dividendPaymentDate;
    private long capitalStock;
    private int employeeCount;
    private CompanyType companyType; // enumに変更
    private MarketType marketType;   // enumに変更
    
    // コンストラクタ
    public StockInfo() {}
    
    public StockInfo(int stockId, String stockName, int dividendPerShare, 
                     MonthDay dividendPaymentDate, long capitalStock, 
                     int employeeCount, CompanyType companyType, MarketType marketType) {
        this.stockId = stockId;
        this.stockName = stockName;
        this.dividendPerShare = dividendPerShare;
        this.dividendPaymentDate = dividendPaymentDate;
        this.capitalStock = capitalStock;
        this.employeeCount = employeeCount;
        this.companyType = companyType;
        this.marketType = marketType;
    }
    
    // CSVの行からStockInfoオブジェクトを作成するファクトリメソッド
    public static StockInfo fromCsvLine(String csvLine) {
        String[] fields = csvLine.split(",");
        if (fields.length != 8) {
            throw new IllegalArgumentException("CSVの列数が正しくありません: " + csvLine);
        }
        
        try {
            int stockId = Integer.parseInt(fields[0].trim());
            String stockName = fields[1].trim();

            int dividendPerShare = Integer.parseInt(fields[2].trim());
            MonthDay dividendPaymentDate = MonthDay.parse(fields[3].trim(), DateTimeFormatter.ofPattern("M月d日"));
            long capitalStock = Long.parseLong(fields[4].trim());
            int employeeCount = Integer.parseInt(fields[5].trim());
            CompanyType companyType = CompanyType.fromString(fields[6].trim());
            MarketType marketType = MarketType.fromString(fields[7].trim());
            
            return new StockInfo(stockId, stockName, dividendPerShare, 
                               dividendPaymentDate, capitalStock, 
                               employeeCount, companyType, marketType);
        } catch (Exception e) {
            throw new IllegalArgumentException("CSVの解析に失敗しました: " + csvLine, e);
        }
    }
    
    // Getter/Setter
    public int getStockId() {
        return stockId;
    }
    
    public void setStockId(int stockId) {
        this.stockId = stockId;
    }
    
    public String getStockName() {
        return stockName;
    }
    
    public void setStockName(String stockName) {
        this.stockName = stockName;
    }
    
    public int getDividendPerShare() {
        return dividendPerShare;
    }

    public void setDividendPerShare(int dividendPerShare) {
        this.dividendPerShare = dividendPerShare;
    }

    public MonthDay getDividendPaymentDate() {
        return dividendPaymentDate;
    }

    public void setDividendPaymentDate(MonthDay dividendPaymentDate) {
        this.dividendPaymentDate = dividendPaymentDate;
    }
    
    public long getCapitalStock() {
        return capitalStock;
    }
    
    public void setCapitalStock(long capitalStock) {
        this.capitalStock = capitalStock;
    }
    
    public int getEmployeeCount() {
        return employeeCount;
    }
    
    public void setEmployeeCount(int employeeCount) {
        this.employeeCount = employeeCount;
    }
    
    public CompanyType getCompanyType() {
        return companyType;
    }
    
    public void setCompanyType(CompanyType companyType) {
        this.companyType = companyType;
    }
    
    public MarketType getMarketType() {
        return marketType;
    }
    
    public void setMarketType(MarketType marketType) {
        this.marketType = marketType;
    }
    
    @Override
    public String toString() {
        return "StockInfo{" +
                "stockId=" + stockId +
                ", stockName='" + stockName + '\'' +
                ", dividendPerShare=" + dividendPerShare +
                ", dividendPaymentDate=" + dividendPaymentDate +
                ", capitalStock=" + capitalStock +
                ", employeeCount=" + employeeCount +
                ", companyType=" + companyType +
                ", marketType=" + marketType +
                '}';
    }

    // 基準価格をintで取得するメソッド（株価表示用）
    public int getBasePriceAsInt() {
        long basePrice = (long) StockPriceCalculator.calculateBasePrice(this);
        // 999円以下に制限して返す
        return (int) Math.min(basePrice, 999);
    }
    
    // 元のメソッドはlongのまま保持
    public long getBasePrice() {
        return (long) StockPriceCalculator.calculateBasePrice(this);
    }
}
