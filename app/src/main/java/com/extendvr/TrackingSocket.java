package com.extendvr;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class TrackingSocket extends WebSocketServer {
    // initialiser
    public TrackingSocket( int port ) {
        super( new InetSocketAddress( port ) );
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake ) {
        System.out.println( conn.getRemoteSocketAddress().getAddress().getHostAddress() + " joined the tracking feed" );
    }

    @Override
    public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
        System.out.println( conn + " left the tracking feed" );
    }

    @Override
    public void onMessage( WebSocket conn, String message ) {

    }

    @Override
    public void onStart() {
        System.out.println("Server started!");
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    @Override
    public void onError( WebSocket conn, Exception ex ) {
        ex.printStackTrace();
        if( conn != null ) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }
}
