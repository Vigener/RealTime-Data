package io.github.vgnri.client;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArraySet;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class StockWebSocketServer extends WebSocketServer {
    private static final CopyOnWriteArraySet<WebSocket> connections = new CopyOnWriteArraySet<>();
    private static StockWebSocketServer instance;

    public StockWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    public static synchronized StockWebSocketServer getInstance(String host, int port) {
        if (instance == null) {
            instance = new StockWebSocketServer(new InetSocketAddress(port));
        }
        return instance;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
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
        System.out.println("Received message from client: " + message);
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        System.out.println("Received binary message from client");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on " + getAddress());
    }

    // 全ての接続されたクライアントにメッセージを送信
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

    // 接続数を取得
    public int getConnectionCount() {
        return connections.size();
    }
}
