package io.github.vgnri.client;

import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 5000; // 任意のポート番号
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started");

        // stock_data.txtをリソースとして読み込む
        InputStream is = Server.class.getClassLoader().getResourceAsStream("stock_data.txt");
        if (is == null) {
            System.err.println("stock_data.txt not found in resources.");
            // エラーが発生した場合、サーバーを終了する
            return;
        }

        while (true) { // 無限ループで繰り返し読み込み・送信
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line = "";
                line = br.readLine(); // 見出し削除
                while ((line = br.readLine()) != null) {
                    System.out.println("Sending data: " + line); // データ送信のログを出力
                    try {
                        Thread.sleep(48); // データ送信間隔を調整
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}