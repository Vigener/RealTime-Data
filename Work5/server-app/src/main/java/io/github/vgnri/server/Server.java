package io.github.vgnri.server;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        // Flink execution environmentのセットアップ
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool params = ParameterTool.fromArgs(args);
        env.getConfig().setGlobalJobParameters(params);

        int port = 5000;  // 任意のポート番号
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server started");

        Socket clientSocket = serverSocket.accept();
        System.out.println("Client accepted");

        // Client.javaがwebsocket通信を完了したことを報告してきてから通信を開始する
        // 報告方法としては、Client.javaがサーバーに対して"WebSocket Connected"という文字列を送信する
        System.out.println("Waiting for WebSocket connection from client...");

        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        String message = "";
        while (true) {
            message = in.readUTF();
            if (message.equals("WebSocket Connected")) {
                break;
            }
        }
        System.out.println("WebSocket Connected");

        // stock_data.txtをリソースとして読み込む
        InputStream is = Server.class.getClassLoader().getResourceAsStream("stock_data.txt");
        if (is == null) {
            System.err.println("stock_data.txt not found in resources.");
            return;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            line = br.readLine(); // 見出し削除

            // Flink DataStreamの作成
            DataStream<String> dataStream = env.fromElements(line);

            while ((line = br.readLine()) != null) {
                String finalLine = line;
                dataStream = dataStream.union(env.fromElements(finalLine)); // 各行をDataStreamに追加
                try {
                    Thread.sleep(48);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // データの変換 (例: カンマ区切りを分割)
            DataStream<String[]> processedStream = dataStream.map(new MapFunction<String, String[]>() {
                @Override
                public String[] map(String value) throws Exception {
                    try {
                        return value.split(",");
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e; // 例外を再スローするか、適切な処理を行う
                    }
                }
            });

            // Flinkジョブの実行 (ここでは標準出力に出力)
            processedStream.print();

            // ストリーミング処理の開始
            try {
                env.execute("Stock Data Streaming");
            } catch (Exception e) {
                e.printStackTrace();
                // 例外処理
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // close the connection
            try {
                if (clientSocket != null) clientSocket.close();
                if (serverSocket != null) serverSocket.close();
                if (in != null) in.close();
            } catch (IOException i) {
                System.out.println(i);
            }
        }
    }
}