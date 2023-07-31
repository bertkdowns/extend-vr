package com.extendvr

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class TrackingSocket(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    //Defines a simple WebSocket server
    //allows MainActivity to override the messageListener to recieve messages
    // that's it!

    open class OnMessageListener {
        open fun onMessage(conn: WebSocket, message: String) {}
    }

    var messageListener = OnMessageListener()

    override fun onMessage(conn: WebSocket, message: String) {
        messageListener.onMessage(conn, message)
    }


    // Other methods I'm not using
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        println(conn.remoteSocketAddress.address.hostAddress + " joined the tracking feed")
    }
    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) { println("$conn left the tracking feed") }
    override fun onStart() { println("Server started!")}
    override fun onError(conn: WebSocket, ex: Exception) { ex.printStackTrace() }
}