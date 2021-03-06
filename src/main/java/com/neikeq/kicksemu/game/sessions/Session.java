package com.neikeq.kicksemu.game.sessions;

import com.neikeq.kicksemu.game.lobby.Lobby;
import com.neikeq.kicksemu.game.lobby.LobbyManager;
import com.neikeq.kicksemu.game.rooms.Room;
import com.neikeq.kicksemu.game.rooms.enums.RoomLeaveReason;
import com.neikeq.kicksemu.game.rooms.RoomManager;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.game.users.UserInfo;

import com.neikeq.kicksemu.network.server.ServerManager;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;

public class Session {

    private final Channel channel;

    private int userId;
    private int playerId;
    private int roomId;
    private int udpPort;
    private int ping;

    private boolean authenticated;
    private boolean udpAuthenticated;
    private boolean observer;

    private final Object locker = new Object();

    /**
     * Write a message to the channel without flushing.<br>
     * Client handler will flush the channel after reading is complete as seen in method:
     * {@link com.neikeq.kicksemu.network.server.tcp.ClientHandler#channelReadComplete}<br>
     * This increases the performance when writing multiple messages during a single reading.
     */
    public synchronized void send(ServerMessage msg) {
        if (getChannel().isOpen()) {
            getChannel().write(msg.getByteBuf());
        }
    }

    /**
     * Write a message to the channel and flush after that.
     * Useful for chat messages and non-response messages.
     */
    public synchronized void sendAndFlush(ServerMessage msg)  {
        if (getChannel().isOpen()) {
            getChannel().writeAndFlush(msg.getByteBuf());
        }
    }

    /** Called when the session leaved a room */
    public void onLeavedRoom() {
        roomId = -1;

        // If session is still alive, add it to the main lobby
        if (authenticated) {
            LobbyManager.addPlayer(playerId);
        }
    }

    /** If the session is inside a room, leave it */
    public boolean leaveRoom(RoomLeaveReason reason) {
        Room room = RoomManager.getRoomById(roomId);

        boolean isInsideRoom = room != null && room.isPlayerIn(playerId);

        // If room exist and player is inside the room
        if (isInsideRoom) {
            room.removePlayer(this, reason);
            sendAndFlush(MessageBuilder.leaveRoom(playerId, reason));
        }

        return isInsideRoom;
    }

    /** Returns the room lobby if player is inside a room, otherwise return main lobby */
    public Lobby getCurrentLobby() {
        if (getRoomId() > 0) {
            return RoomManager.getRoomById(getRoomId()).getRoomLobby();
        } else {
            return LobbyManager.getMainLobby();
        }
    }

    public void close() {
        if (getChannel().isOpen()) {
            // Ensure we have sent everything
            getChannel().flush();
            // Close connection with the client
            getChannel().close();
        }

        // If session information were not yet cleared
        if (authenticated) {
            setAuthenticated(false);
            setUdpAuthenticated(false);

            // Update user status on database
            UserInfo.setOnline(false, userId);

            if (playerId > 0) {
                // Remove session from the list of connected clients
                ServerManager.removePlayer(playerId);
                // If session is in the main lobby, leave it
                LobbyManager.removePlayer(playerId);
            }

            // If session is inside a room, leave it
            leaveRoom(RoomLeaveReason.DISCONNECTED);
        }
    }
    
    public Session(Channel channel) {
        this.channel = channel;
        setRoomId(-1);
        setObserver(false);
        setUdpPort(-1);

        setAuthenticated(false);
        setUdpAuthenticated(false);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean value) {
        authenticated = value;
    }

    public Channel getChannel() {
        return channel;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int id) {
        userId = id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int id) {
        playerId = id;
    }

    public boolean isUdpAuthenticated() {
        return udpAuthenticated;
    }

    public void setUdpAuthenticated(boolean udpAuthenticated) {
        this.udpAuthenticated = udpAuthenticated;
    }

    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress)channel.remoteAddress();
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public Object getLocker() {
        return locker;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public boolean isObserver() {
        return observer;
    }

    public void setObserver(boolean observer) {
        this.observer = observer;
    }

    public int getPing() {
        return ping;
    }

    public void setPing(int ping) {
        this.ping = ping;
    }
}
