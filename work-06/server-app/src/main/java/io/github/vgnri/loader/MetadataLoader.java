package io.github.vgnri.loader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.ConcurrentHashMap;

import io.github.vgnri.config.Config;
import io.github.vgnri.model.ShareholderInfo;
import io.github.vgnri.model.StockInfo;

/**
 * CSVファイルからメタデータを読み込むためのユーティリティクラス
 */
public class MetadataLoader {

    /**
     * 株メタデータをCSVファイルから読み込む
     * @param csvFilePath CSVファイルのパス
     * @return 株ID -> 株情報のマップ
     */
    public static ConcurrentHashMap<Integer, StockInfo> loadStockMetadata(String csvFilePath) {
        System.out.println("株メタデータを読み込み中: " + csvFilePath);
        
        ConcurrentHashMap<Integer, StockInfo> stockMetadata = new ConcurrentHashMap<>();
        
        int maxLines = Config.DEFAULT_STOCK_COUNT + 1;
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null && lineNumber < maxLines) {
            // while ((line = reader.readLine()) != null) { // 本番用
            lineNumber++;

            // ヘッダ行をスキップ
            if (lineNumber == 1) {
                System.out.println("ヘッダ: " + line);
                continue;
            }

            // 空行をスキップ
            if (line.trim().isEmpty()) {
                continue;
            }
                
                try {
                    StockInfo stockInfo = StockInfo.fromCsvLine(line);
                    stockMetadata.put(stockInfo.getStockId(), stockInfo);
                    System.out.println("読み込み完了: " + stockInfo.getStockName() + " (ID: " + stockInfo.getStockId() + ")");
                } catch (Exception e) {
                    System.err.println("行 " + lineNumber + " の解析に失敗: " + line);
                    System.err.println("エラー: " + e.getMessage());
                }
            }
            
            System.out.println("株メタデータ読み込み完了: " + stockMetadata.size() + " 件");
            
        } catch (Exception e) {
            System.err.println("CSVファイルの読み込みに失敗: " + e.getMessage());
            e.printStackTrace();
        }
        
        return stockMetadata;
    }

    /**
     * 株主メタデータをCSVファイルから読み込む
     * @param csvFilePath CSVファイルのパス
     * @return 株主ID -> 株主情報のマップ
     */
    public static ConcurrentHashMap<Integer, ShareholderInfo> loadShareholderMetadata(String csvFilePath) {
        System.out.println("株主メタデータを読み込み中: " + csvFilePath);
        
        ConcurrentHashMap<Integer, ShareholderInfo> shareholderMetadata = new ConcurrentHashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            int lineNumber = 0;
            
            int maxLines = Config.DEFAULT_SHAREHOLDER_COUNT + 1;
            while ((line = reader.readLine()) != null && lineNumber < maxLines) {
            // while ((line = reader.readLine()) != null) { // 本番用
                lineNumber++;
                
                // ヘッダ行をスキップ
                if (lineNumber == 1) {
                    System.out.println("ヘッダ: " + line);
                    continue;
                }
                
                // 空行をスキップ
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    ShareholderInfo shareholderInfo = ShareholderInfo.fromCsvLine(line);
                    shareholderMetadata.put(shareholderInfo.getShareholderId(), shareholderInfo);
                    System.out.println("読み込み完了: " + shareholderInfo.getShareholderName() + " (ID: " + shareholderInfo.getShareholderId() + ")");
                } catch (Exception e) {
                    System.err.println("行 " + lineNumber + " の解析に失敗: " + line);
                    System.err.println("エラー: " + e.getMessage());
                }
            }
            
            System.out.println("株主メタデータ読み込み完了: " + shareholderMetadata.size() + " 件");
            
        } catch (Exception e) {
            System.err.println("CSVファイルの読み込みに失敗: " + e.getMessage());
            e.printStackTrace();
        }
        
        return shareholderMetadata;
    }
}
