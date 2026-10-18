package com.shike.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务（来单提醒 / 催单提醒）
 *
 * 相比教程原版改了三处，都是线上会真的出问题的地方：
 *
 * 1. HashMap -> ConcurrentHashMap
 *    原版用 HashMap 存会话。Tomcat 每个连接一个线程，@OnClose 删除与 sendToAllClient 遍历
 *    并发执行时，for 循环里的 iterator.next() 会抛 ConcurrentModificationException；
 *    而 try/catch 只包住了 sendText 那一行 —— 异常直接冲出方法，整次推送失败（消息静默丢失）。
 *
 * 2. 发送失败要移除会话 + 加锁
 *    - BasicRemote.sendText 不是线程安全的。多线程同时写同一个 session 会抛
 *      "The remote endpoint was in state [TEXT_FULL_WRITING]"，被 catch 掉后消息就没了 ——
 *      表现就是"顾客下单了，但后台不弹"。
 *    - 会话已断（浏览器关了/刷新了）时必须从 map 摘掉，否则每次群发都在给死连接发消息。
 *
 * 3. 补 @OnError
 *    连接异常时也要清会话，否则 sessionMap 会越积越多。
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    // 会话表：sid -> Session（并发容器，见类注释第 1 点）
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        SESSION_MAP.put(sid, session);
        log.info("WebSocket 连接建立：sid={}，当前在线 {} 个", sid, SESSION_MAP.size());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.debug("收到客户端消息：sid={}，message={}", sid, message);
    }

    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        SESSION_MAP.remove(sid);
        log.info("WebSocket 连接断开：sid={}，当前在线 {} 个", sid, SESSION_MAP.size());
    }

    @OnError
    public void onError(Session session, @PathParam("sid") String sid, Throwable error) {
        SESSION_MAP.remove(sid);
        log.warn("WebSocket 出错，已移除会话：sid={}，原因={}", sid, error.toString());
    }

    /**
     * 群发消息给所有管理端页面
     *
     * @param message JSON 字符串，形如 {"type":1,"orderId":7,"content":"订单号：xxx"}
     */
    public void sendToAllClient(String message) {
        if (SESSION_MAP.isEmpty()) {
            log.debug("当前没有 WebSocket 客户端在线，跳过推送：{}", message);
            return;
        }
        SESSION_MAP.forEach((sid, session) -> {
            if (session == null || !session.isOpen()) {
                SESSION_MAP.remove(sid);
                return;
            }
            try {
                // 同一个 session 的写入串行化，避免并发写导致 TEXT_FULL_WRITING
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
                log.info("推送成功：sid={}，内容={}", sid, message);
            } catch (Exception e) {
                // 发送失败说明这条连接已经废了，直接摘掉，别留着重试
                SESSION_MAP.remove(sid);
                log.warn("推送失败，已移除会话：sid={}，原因={}", sid, e.getMessage());
            }
        });
    }
}
