package com.muite.zongshe1.utils;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@ServerEndpoint("/vehicle-simulation")
public class VehicleSimulationSocket {

    // 保存所有连接的会话（支持多前端）
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("前端连接建立，当前连接数: " + sessions.size());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("前端连接关闭，当前连接数: " + sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket 错误: " + error.getMessage());
    }

    // 工具方法：向所有前端推送消息
    public static void broadcast(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        System.err.println("推送失败: " + e.getMessage());
                    }
                }
            }
        }
    }
}