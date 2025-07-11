package io.github.vgnri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RunConfiguration {
    private static final List<Process> processes = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== 株式分析システム起動中 ===");
        
        try {
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
    
    private static void startServices() throws IOException, InterruptedException {
        String classpath = System.getProperty("java.class.path");
        
        // StockPrice起動
        System.out.println("StockPrice サービス起動中...");
        ProcessBuilder stockPriceBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.StockPrice"
        );
        Process stockPriceProcess = stockPriceBuilder.start();
        processes.add(stockPriceProcess);
        
        // 起動待機
        TimeUnit.SECONDS.sleep(2);
        
        // Transaction起動
        System.out.println("Transaction サービス起動中...");
        ProcessBuilder transactionBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.Transaction"
        );
        Process transactionProcess = transactionBuilder.start();
        processes.add(transactionProcess);
        
        // 起動待機
        TimeUnit.SECONDS.sleep(2);
        
        System.out.println("全サービス起動完了");
    }
    
    private static void cleanup() {
        System.out.println("サービス終了中...");
        for (Process process : processes) {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        processes.clear();
        System.out.println("システム終了完了");
    }
    
    // シャットダウンフック
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RunConfiguration::cleanup));
    }
}

