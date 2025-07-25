package io.github.vgnri.config;

import java.io.File;
import java.net.URL;

/**
 * プロジェクト全体で使用する設定値を管理するクラス
 */
public class Config {

    // サーバー関連の設定
    public static final int STOCK_PRICE_PORT = 2001; // StockPriceデータのポート番号
    public static final int TRANSACTION_PORT = 2002; // Transactionデータのポート番号
    
    
    // 株価関連の設定
    public static final int MAX_STOCK_COUNT = 3000;        // 最大銘柄数
    public static final int DEFAULT_STOCK_COUNT = 300;       // デフォルト銘柄数
    // public static final int DEFAULT_STOCK_COUNT = 5;       // デフォルト銘柄数
    
    // 株主関連の設定
    public static final int MAX_SHAREHOLDER_COUNT = 5194; // 最大株主数
    public static final int DEFAULT_SHAREHOLDER_COUNT = 50; // デフォルト株主数
    // public static final int DEFAULT_SHAREHOLDER_COUNT = 5; // デフォルト株主数

    // 株取引関連の設定
    public static final int MAX_TRADES_PER_UPDATE = 1000;     // 最大取引数(更新ごとの)
    // public static final int DEFAULT_TRADES_PER_UPDATE = 50;  // デフォルト取引数(更新ごとの)
    public static final int DEFAULT_TRADES_PER_UPDATE = 10;  // デフォルト取引数(更新ごとの)

    // 現在の設定値（実行時に変更可能）
    private static int currentStockCount = DEFAULT_STOCK_COUNT;
    private static int currentShareholderCount = DEFAULT_SHAREHOLDER_COUNT;
    private static int currentTradesPerUpdate = DEFAULT_TRADES_PER_UPDATE;

    // 株価更新間隔
    public static final int PRICE_UPDATE_INTERVAL_MS = 100;
    
    // 株取引更新間隔
    public static final int TRADE_UPDATE_INTERVAL_MS = 100;

    // スライディングウィンドウ関連(Time Window)
    // public static final int SLIDING_WINDOW_SIZE_MS = 200; // スライディングウィンドウのサイズ
    public static final int SLIDING_WINDOW_SIZE_MS = 1000; // スライディングウィンドウのサイズ(1000ms = 1秒)
    // public static final int SLIDING_WINDOW_STEP_MS = 100; // スライディングウィンドウのステップ
    public static final int SLIDING_WINDOW_STEP_MS = 500; // スライディングウィンドウのステップ(500ms = 0.5秒)



    // CSVファイルパス - 動的に解決
    private static String getResourcePath(String resourceName) {
        // 1. クラスパスから探す
        URL resource = Config.class.getClassLoader().getResource(resourceName);
        if (resource != null) {
            return resource.getPath();
        }
        
        // 2. 現在のワーキングディレクトリから探す
        String[] possiblePaths = {
            "src/main/resources/" + resourceName,
            "server-app/src/main/resources/" + resourceName,
            "resources/" + resourceName,
            resourceName
        };
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return path;
            }
        }
        
        // 3. フォールバック（デフォルトパス）
        System.err.println("Warning: " + resourceName + " not found, using default path");
        return "src/main/resources/" + resourceName;
    }
    
    // 株価データ
    public static final String STOCK_PRICE_CSV_PATH = getResourcePath("stock_price_data.csv");
    // 株メタデータ
    public static final String STOCK_META_CSV_PATH = getResourcePath("stock_metadata.csv");
    // 株主データ
    public static final String SHAREHOLDER_CSV_PATH = getResourcePath("shareholder_metadata.csv");
    // つくば市のデータ
    public static final String TSUKUBA_CSV_PATH = getResourcePath("tsukuba_metadata.csv");
    // 初期価格データ
    // public static final String INITIAL_PRICE_CSV_PATH = getResourcePath("initial_price_data.csv");
    public static final String INITIAL_PRICE_CSV_PATH = "server-app/src/main/resources/initial_price_data.csv";

    public static final int WEBSOCKET_PORT = 3000; // WebSocketのポート番号
    public static final int PRICE_MANAGER_PORT = 2003;


    
    // 現在の銘柄数を取得
    public static int getCurrentStockCount() {
        return currentStockCount;
    }
    
    // 現在の銘柄数を設定
    public static void setCurrentStockCount(int stockCount) {
        if (stockCount > 0 && stockCount <= MAX_STOCK_COUNT) {
            currentStockCount = stockCount;
            System.out.println("銘柄数を " + stockCount + " に設定しました");
        } else {
            throw new IllegalArgumentException("銘柄数は1以上" + MAX_STOCK_COUNT + "以下である必要があります");
        }
    }

    // 現在の株主数を取得
    public static int getCurrentShareholderCount() {
        return currentShareholderCount;
    }
    
    // 現在の株主数を設定
    public static void setCurrentShareholderCount(int shareholderCount) {
        if (shareholderCount > 0 && shareholderCount <= MAX_SHAREHOLDER_COUNT) {
            currentShareholderCount = shareholderCount;
            System.out.println("株主数を " + shareholderCount + " に設定しました");
        } else {
            throw new IllegalArgumentException("株主数は1以上" + MAX_SHAREHOLDER_COUNT + "以下である必要があります");
        }
    }
    
    // 現在の更新あたり取引数を取得
    public static int getCurrentTradesPerUpdate() {
        return currentTradesPerUpdate;
    }
    
    // 現在の更新あたり取引数を設定
    public static void setCurrentTradesPerUpdate(int tradesPerUpdate) {
        if (tradesPerUpdate > 0 && tradesPerUpdate <= MAX_TRADES_PER_UPDATE) {
            currentTradesPerUpdate = tradesPerUpdate;
            System.out.println("更新あたり取引数を " + tradesPerUpdate + " に設定しました");
        } else {
            throw new IllegalArgumentException("更新あたり取引数は1以上" + MAX_TRADES_PER_UPDATE + "以下である必要があります");
        }
    }
    
    // 設定情報を表示
    public static void printCurrentConfig() {
        System.out.println("=== 現在の設定 ===");
        System.out.println("銘柄数: " + currentStockCount + " / " + MAX_STOCK_COUNT);
        System.out.println("株主数: " + currentShareholderCount + " / " + MAX_SHAREHOLDER_COUNT);
        System.out.println("更新間隔: " + PRICE_UPDATE_INTERVAL_MS + "ms");
        System.out.println("================");
    }
}
