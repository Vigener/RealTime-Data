package io.github.vgnri;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.vgnri.config.Config;
import io.github.vgnri.model.PriceManager;

public class RunConfiguration {
    private static final List<Process> processes = new ArrayList<>();
    
    // ログファイル関連
    private static PrintWriter logWriter;
    private static final String LOG_FILE = "system.log";
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    public static void main(String[] args) {
        try {
            // ログファイルを初期化
            initLogFile();
            
            logAndPrint("=== 株式分析システム起動中 ===");
            
            // 起動前ポートチェック
            checkAndCleanupPorts();
            
            // 依存サービス起動
            startServices();
            
            // メインアプリケーション起動
            logAndPrint("StockProcessor を起動中...");
            StockProcessor.main(args);
            
        } catch (Exception e) {
            String errorMsg = "システム起動エラー: " + e.getMessage();
            logAndPrintError(errorMsg);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }
    
    /**
     * ログファイルを初期化
     */
    private static void initLogFile() {
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, false)); // 上書きモード
            
            // ヘッダー情報をログに書き込み
            String header = "=== システムログ - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ===";
            logWriter.println(header);
            logWriter.flush();
            
            System.out.println("ログファイルを初期化しました: " + LOG_FILE);
            
        } catch (IOException e) {
            System.err.println("ログファイル初期化エラー: " + e.getMessage());
        }
    }
    
    /**
     * コンソールとログファイルの両方に出力
     */
    private static void logAndPrint(String message) {
        String timestampedMessage = LocalDateTime.now().format(timeFormatter) + " " + message;
        
        // コンソール出力
        System.out.println(timestampedMessage);
        
        // ログファイル出力
        if (logWriter != null) {
            logWriter.println(timestampedMessage);
            logWriter.flush();
        }
    }
    
    /**
     * エラーメッセージをコンソールとログファイルの両方に出力
     */
    private static void logAndPrintError(String message) {
        String timestampedMessage = LocalDateTime.now().format(timeFormatter) + " ERROR: " + message;
        
        // コンソール出力
        System.err.println(timestampedMessage);
        
        // ログファイル出力
        if (logWriter != null) {
            logWriter.println(timestampedMessage);
            logWriter.flush();
        }
    }
    
    private static void checkAndCleanupPorts() throws IOException, InterruptedException {
        logAndPrint("起動前ポートチェック実行中...");
        
        int[] portsToCheck = {Config.TRANSACTION_PORT, Config.PRICE_MANAGER_PORT, 8080, 3000};
        
        for (int port : portsToCheck) {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", 
                "lsof -ti:" + port + " 2>/dev/null || echo 'PORT_FREE'");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                
                if (line != null && !line.equals("PORT_FREE")) {
                    logAndPrint("ポート " + port + " が使用中です。プロセスを終了します: PID=" + line.trim());
                    
                    ProcessBuilder killPb = new ProcessBuilder("kill", "-9", line.trim());
                    killPb.start().waitFor(2, TimeUnit.SECONDS);
                    
                    logAndPrint("ポート " + port + " を解放しました");
                    TimeUnit.SECONDS.sleep(1);
                } else {
                    logAndPrint("ポート " + port + " は利用可能です");
                }
            }
        }
    }
    
    private static void startServices() throws IOException, InterruptedException {
        String classpath = System.getProperty("java.class.path");
        
        // 1. Transaction起動（デバッグ出力付き）
        logAndPrint("Transaction サーバー起動中...");
        ProcessBuilder transactionBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.Transaction"
        );
        
        Process transactionProcess = transactionBuilder.start();
        processes.add(transactionProcess);
        
        // Transaction出力監視開始
        startOutputMonitor(transactionProcess, "[Transaction]");
        
        // Transaction起動待機
        logAndPrint("Transaction起動待機中... (3秒)");
        TimeUnit.SECONDS.sleep(3);
        logAndPrint("Transaction サーバー起動完了");
        
        // 2. PriceManager起動（デバッグ出力付き）
        logAndPrint("PriceManager サーバー起動中...");
        ProcessBuilder priceManagerBuilder = new ProcessBuilder(
            "java", "-cp", classpath, "io.github.vgnri.model.PriceManager"
        );
        
        Process priceManagerProcess = priceManagerBuilder.start();
        processes.add(priceManagerProcess);
        
        // PriceManager出力監視開始
        startOutputMonitor(priceManagerProcess, "[PriceManager]");
        
        // PriceManager起動待機
        logAndPrint("PriceManager起動待機中... (5秒)");
        TimeUnit.SECONDS.sleep(5);
        logAndPrint("PriceManager サーバー起動完了");
        
        // プロセス状態確認
        checkProcessStatus();
        
        logAndPrint("=== 全サーバー起動完了 ===");
        logAndPrint("データフロー: Transaction(生成) → PriceManager(価格付与) → StockProcessor(処理)");
        logAndPrint("=== 以下、各サービスの出力を監視します ===");
        logAndPrint("ログファイル: " + LOG_FILE + " で詳細を確認できます");
    }
    
    /**
     * プロセスの出力を監視してプレフィックス付きで表示（ログファイルにも出力）
     */
    private static void startOutputMonitor(Process process, String prefix) {
        // 標準出力監視
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String message = prefix + " " + line;
                    logAndPrint(message);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    logAndPrintError(prefix + " 出力読み取りエラー: " + e.getMessage());
                }
            }
        });
        stdoutThread.setDaemon(true);
        stdoutThread.setName(prefix + "-stdout");
        stdoutThread.start();
        
        // エラー出力監視
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String message = prefix + " ERROR: " + line;
                    logAndPrintError(message);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    logAndPrintError(prefix + " エラー出力読み取りエラー: " + e.getMessage());
                }
            }
        });
        stderrThread.setDaemon(true);
        stderrThread.setName(prefix + "-stderr");
        stderrThread.start();
    }
    
    /**
     * プロセス状態確認
     */
    private static void checkProcessStatus() {
        logAndPrint("=== プロセス状態確認 ===");
        for (int i = 0; i < processes.size(); i++) {
            Process process = processes.get(i);
            String processName = (i == 0) ? "Transaction" : "PriceManager";
            
            if (process.isAlive()) {
                logAndPrint(processName + " プロセス: 正常動作中 (PID=" + process.pid() + ")");
            } else {
                logAndPrintError(processName + " プロセス: 停止しています! (終了コード=" + process.exitValue() + ")");
                
                // プロセスが停止している場合、残りの出力を確認
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    logAndPrintError(processName + " エラー出力:");
                    while ((line = reader.readLine()) != null) {
                        logAndPrintError("  " + line);
                    }
                } catch (IOException e) {
                    logAndPrintError(processName + " エラー出力読み取り失敗: " + e.getMessage());
                }
            }
        }
        
        // ポート使用状況も確認
        checkPortsUsage();
    }
    
    /**
     * ポート使用状況確認
     */
    private static void checkPortsUsage() {
        logAndPrint("=== ポート使用状況確認 ===");
        int[] ports = {Config.TRANSACTION_PORT, Config.PRICE_MANAGER_PORT, 8080};
        String[] portNames = {"Transaction", "PriceManager", "WebSocket"};
        
        for (int i = 0; i < ports.length; i++) {
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", 
                    "netstat -tlnp 2>/dev/null | grep :" + ports[i] + " || echo 'NOT_LISTENING'");
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    
                    if (line != null && line.contains("NOT_LISTENING")) {
                        logAndPrintError(portNames[i] + " ポート " + ports[i] + ": リッスンしていません");
                    } else if (line != null) {
                        logAndPrint(portNames[i] + " ポート " + ports[i] + ": リッスン中");
                    }
                }
            } catch (Exception e) {
                logAndPrintError(portNames[i] + " ポート確認エラー: " + e.getMessage());
            }
        }
    }
    
    private static void cleanup() {
        logAndPrint("システム終了処理開始...");
        
        try {
            // StockProcessorの安全な停止
            logAndPrint("StockProcessor停止中...");
            try {
                StockProcessor.requestShutdown();
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                logAndPrintError("StockProcessor停止エラー: " + e.getMessage());
            }
            
            // PriceManagerの安全な停止
            logAndPrint("PriceManager停止中...");
            try {
                PriceManager.shutdown();
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                logAndPrintError("PriceManager停止エラー: " + e.getMessage());
            }
            
            // 子プロセスの強制終了
            logAndPrint("子プロセス終了中...");
            for (Process process : processes) {
                if (process.isAlive()) {
                    logAndPrint("プロセス強制終了: PID=" + process.pid());
                    process.destroyForcibly();
                    
                    try {
                        boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
                        if (terminated) {
                            logAndPrint("プロセス正常終了: PID=" + process.pid());
                        } else {
                            logAndPrintError("プロセス終了タイムアウト: PID=" + process.pid());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            processes.clear();
            
            logAndPrint("システム終了処理完了");
            
        } catch (Exception e) {
            logAndPrintError("システム終了処理エラー: " + e.getMessage());
        } finally {
            // ログファイルを閉じる
            if (logWriter != null) {
                logWriter.println("=== ログ終了 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ===");
                logWriter.close();
                System.out.println("ログファイルを閉じました: " + LOG_FILE);
            }
        }
    }
    
    // シャットダウンフック
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RunConfiguration::cleanup));
    }
}