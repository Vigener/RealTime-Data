package io.github.vgnri.server;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArraySet;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

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
        broadcast("Message from server: " + message);
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
}