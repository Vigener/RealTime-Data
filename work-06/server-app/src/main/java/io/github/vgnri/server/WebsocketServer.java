package io.github.vgnri.server;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CopyOnWriteArraySet;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.vgnri.StockProcessor;

public class WebsocketServer extends WebSocketServer implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_PORT = 3000;
    private static final CopyOnWriteArraySet<WebSocket> connections = new CopyOnWriteArraySet<>();
    private static WebsocketServer instance;

    public WebsocketServer(InetSocketAddress address) {
        super(address);
    }

    public WebsocketServer() {
        super(new InetSocketAddress(DEFAULT_PORT));
    }

    public static synchronized WebsocketServer getInstance(String host, int port) {
        if (instance == null) {
            instance = new WebsocketServer(new InetSocketAddress(host, port));
        }
        return instance;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        conn.send("hello!!");
        System.out.println("New WebSocket connection: " + conn.getRemoteSocketAddress());
        System.out.println("Total connections: " + connections.size());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        System.out.println("WebSocket connection closed: " + conn.getRemoteSocketAddress());
        System.out.println("Total connections: " + connections.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message from client: " + message);
        try {
            JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            if ("select_shareholder".equals(type) && obj.has("shareholderId")) {
                int shareholderId = obj.get("shareholderId").getAsInt();
                // IDをStockProcessorに伝達（送信はaggregateAndSendで行う）
                StockProcessor.setSelectedShareholderId(shareholderId);
            } else {
                broadcast("Message from server: " + message);
            }
        } catch (Exception e) {
            System.err.println("WebSocket受信エラー: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        System.out.println("Received binary message from client");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        if (conn != null) {
            connections.remove(conn);
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on " + getAddress());
    }

    public void broadcast(String message) {
        for (WebSocket conn : connections) {
            if (conn.isOpen()) {
                try {
                    conn.send(message);
                } catch (Exception e) {
                    System.err.println("Failed to send message to client: " + e.getMessage());
                }
            }
        }
    }

    public int getConnectionCount() {
        return connections.size();
    }
    
    @Override
    public void stop() throws InterruptedException {
        System.out.println("WebSocketサーバー停止開始...");
        
        try {
            // 1. 全てのクライアント接続を明示的に閉じる
            for (WebSocket conn : getConnections()) {
                if (conn != null && conn.isOpen()) {
                    System.out.println("クライアント接続をクローズ中: " + conn.getRemoteSocketAddress());
                    conn.close();
                }
            }
            
            // 2. 少し待機してから親クラスのstop()を呼び出し
            Thread.sleep(1000);
            
            // 3. 親クラスのstop()を呼び出し（タイムアウト付き）
            super.stop(5000); // 5秒でタイムアウト
            
            System.out.println("WebSocketサーバー停止完了");
            
        } catch (Exception e) {
            System.err.println("WebSocketサーバー停止エラー: " + e.getMessage());
            
            // 強制停止を試行
            try {
                super.stop(1000); // 1秒でタイムアウト
            } catch (Exception forceStopEx) {
                System.err.println("WebSocketサーバー強制停止もエラー: " + forceStopEx.getMessage());
            }
        }
    }
    
    // ポート解放を確実にするメソッド
    public void forceStop() {
        try {
            // 親クラスの protected serverSocketChannel フィールドにアクセス
            Field field = WebSocketServer.class.getDeclaredField("serverSocketChannel");
            field.setAccessible(true);
            Object channelObj = field.get(this);
            if (channelObj instanceof ServerSocketChannel) {
                ServerSocketChannel channel = (ServerSocketChannel) channelObj;
                if (channel != null && channel.isOpen()) {
                    channel.close();
                    System.out.println("ServerSocketChannel強制クローズ完了");
                }
            }
        } catch (Exception e) {
            System.err.println("ServerSocketChannel強制クローズエラー: " + e.getMessage());
        }
    }

    public boolean isClosed() {
        // try {
        //     Field field = WebSocketServer.class.getDeclaredField("serverSocketChannel");
        //     field.setAccessible(true);
        //     Object channelObj = field.get(this);
        //     if (channelObj instanceof ServerSocketChannel) {
        //         try (ServerSocketChannel channel = (ServerSocketChannel) channelObj) {
        //             return !channel.isOpen();
        //         }
        //     }
        // } catch (Exception e) {
        //     System.err.println("WebSocketサーバー状態取得エラー: " + e.getMessage());
        // }
        return true; // 例外が発生した場合は閉じているとみなす
    }
}