package io.github.vgnri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.vgnri.config.Config;
import io.github.vgnri.model.PriceManager;

public class RunConfiguration {
    private static final List<Process> processes = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== 株式分析システム起動中 ===");
        
        try {
            // 起動前ポートチェック
            checkAndCleanupPorts();
            
            // 依存サービス起動
            startServices();
            
            // メインアプリケーション起動
            System.out.println("StockProcessor を起動中...");
            // StockProcessor.main(args);
            StockProcessor.main(args);
            
        } catch (Exception e) {
            System.err.println("システム起動エラー: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // クリーンアップ
            cleanup();
        }
    }
    
    private static void checkAndCleanupPorts() throws IOException, InterruptedException {
        System.out.println("起動前ポートチェック実行中...");
        
        int[] portsToCheck = {Config.TRANSACTION_PORT, Config.PRICE_MANAGER_PORT, 8080, 3000}; // WebSocketポートも含める
        
        for (int port : portsToCheck) {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", 
                "lsof -ti:" + port + " 2>/dev/null || echo 'PORT_FREE'");
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                
                if (line != null && !line.equals("PORT_FREE")) {
                    System.out.println("ポート " + port + " が使用中です。プロセスを終了します: PID=" + line.trim());
                    
                    // プロセスを終了
                    ProcessBuilder killPb = new ProcessBuilder("kill", "-9", line.trim());
                    killPb.start().waitFor(2, TimeUnit.SECONDS);
                    
                    System.out.println("ポート " + port + " を解放しました");
                    TimeUnit.SECONDS.sleep(1);
                } else {
                    System.out.println("ポート " + port + " は利用可能です");
                }
            }
        }
    }
    
    private static void startServices() throws IOException, InterruptedException {
        String classpath = System.getProperty("java.class.path");
        
        // 1. Transaction起動（独立サーバー）
        System.out.println("Transaction サーバー起動中...");
        ProcessBuilder transactionBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.Transaction"
        );
        Process transactionProcess = transactionBuilder.start();
        processes.add(transactionProcess);
        
        // Transaction起動待機
        TimeUnit.SECONDS.sleep(3);
        System.out.println("Transaction サーバー起動完了（ポート: " + Config.TRANSACTION_PORT + "）");
        
        // 2. PriceManager起動（独立サーバー）
        System.out.println("PriceManager サーバー起動中...");
        ProcessBuilder priceManagerBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.PriceManager"
        );
        Process priceManagerProcess = priceManagerBuilder.start();
        processes.add(priceManagerProcess);
        
        // PriceManager起動待機
        TimeUnit.SECONDS.sleep(5); // PriceManagerがTransactionに接続する時間を確保
        System.out.println("PriceManager サーバー起動完了（ポート: " + Config.PRICE_MANAGER_PORT + "）");
        
        System.out.println("=== 全サーバー起動完了 ===");
        System.out.println("データフロー: Transaction(生成) → PriceManager(価格付与) → StockProcessor(処理)");
    }
    
    private static void cleanup() {
        System.out.println("システム終了処理開始...");
        
        try {
            // StockProcessorの安全な停止
            System.out.println("StockProcessor停止中...");
            try {
                StockProcessor.requestShutdown();
                TimeUnit.SECONDS.sleep(2); // 停止処理の完了を待機
            } catch (Exception e) {
                System.err.println("StockProcessor停止エラー: " + e.getMessage());
            }
            
            // PriceManagerの安全な停止
            System.out.println("PriceManager停止中...");
            try {
                PriceManager.shutdown();
                TimeUnit.SECONDS.sleep(2); // 停止処理の完了を待機
            } catch (Exception e) {
                System.err.println("PriceManager停止エラー: " + e.getMessage());
            }
            
            // 子プロセスの強制終了（最後の手段）
            System.out.println("子プロセス終了中...");
            for (Process process : processes) {
                if (process.isAlive()) {
                    System.out.println("プロセス強制終了: PID=" + process.pid());
                    process.destroyForcibly();
                    
                    try {
                        boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
                        if (terminated) {
                            System.out.println("プロセス正常終了: PID=" + process.pid());
                        } else {
                            System.err.println("プロセス終了タイムアウト: PID=" + process.pid());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            processes.clear();
            
        } catch (Exception e) {
            System.err.println("システム終了処理エラー: " + e.getMessage());
        }
        
        System.out.println("システム終了処理完了");
    }
    
    // シャットダウンフック
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RunConfiguration::cleanup));
    }
}

