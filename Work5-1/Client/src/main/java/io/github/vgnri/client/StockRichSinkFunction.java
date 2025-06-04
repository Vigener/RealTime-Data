package io.github.vgnri.client;

import java.io.OutputStream;
import java.net.Socket;

import org.apache.flink.streaming.api.functions.sink.legacy.RichSinkFunction;

public class StockRichSinkFunction extends RichSinkFunction<String> {
    private final String host;
    private final int port;

    public StockRichSinkFunction(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        while (true) {  // 無限ループで接続を試みる
            try (Socket socket = new Socket(host, port);
                 OutputStream outputStream = socket.getOutputStream()) {
                // データを送信
                outputStream.write(value.getBytes("UTF-8"));
                outputStream.flush();
                return;  // 成功したら終了
            } catch (Exception e) {
                System.err.println("Failed to connect to " + host + ":" + port + ". Retrying...");
                Thread.sleep(1000);  // 1秒待機して再試行
            }
        }
    }
}
