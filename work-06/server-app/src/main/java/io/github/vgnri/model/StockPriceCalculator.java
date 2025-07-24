package io.github.vgnri.model;

import java.util.Random;

public class StockPriceCalculator {
    private static final Random random = new Random();
    
    public static long calculateBasePrice(StockInfo stockInfo) {
        int dividendPerShare = stockInfo.getDividendPerShare();
        long capitalStock = stockInfo.getCapitalStock();
        StockInfo.CompanyType companyType = stockInfo.getCompanyType();
        
        // 1. 配当利回りベースの価格計算
        double dividendYield = getDividendYieldByCompanyType(companyType);
        double priceFromDividend = dividendPerShare / (dividendYield / 100.0);
        
        // 2. 資本金ベースの価格計算
        double marketCapMultiplier = getMarketCapMultiplier(companyType);
        long estimatedMarketCap = (long)(capitalStock * marketCapMultiplier);
        
        // 発行済み株式数を資本金から推定（1株あたり500円と仮定）
        long estimatedShares = capitalStock / 500;
        double priceFromMarketCap = (double)estimatedMarketCap / estimatedShares;
        
        // 3. 両方の価格を重み付き平均
        double basePrice = (priceFromDividend * 0.4) + (priceFromMarketCap * 0.6);
        
        // 4. ランダム性を追加（±20%）
        double randomFactor = 0.8 + (random.nextDouble() * 0.4); // 0.8-1.2の範囲
        
        // 5. 最終価格（100円以上になるよう調整）
        double finalPrice = Math.max(100, basePrice * randomFactor);
        
        return Math.round(finalPrice);
    }
    
    private static double getDividendYieldByCompanyType(StockInfo.CompanyType type) {
        // 配当利回り（%）
        switch (type) {
            case LARGE:  return 1.5 + random.nextGaussian() * 0.5; // 1-2%程度
            case MEDIUM: return 2.5 + random.nextGaussian() * 0.7; // 1.8-3.2%程度
            case SMALL:  return 3.5 + random.nextGaussian() * 1.0; // 2.5-4.5%程度
            default:     return 2.5;
        }
    }
    
    private static double getMarketCapMultiplier(StockInfo.CompanyType type) {
        // 時価総額 = 資本金 × この倍率
        switch (type) {
            case LARGE:  return 3.0 + random.nextGaussian() * 1.0;  // 2-4倍
            case MEDIUM: return 2.0 + random.nextGaussian() * 0.7;  // 1.3-2.7倍
            case SMALL:  return 1.5 + random.nextGaussian() * 0.5;  // 1-2倍
            default:     return 2.0;
        }
    }
}
