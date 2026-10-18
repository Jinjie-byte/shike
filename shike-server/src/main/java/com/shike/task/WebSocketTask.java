package com.shike.task;

import com.shike.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 测试任务（教程里的演示用，**已停用**）
 *
 * 原实现每 5 秒向所有管理端页面广播一句 "这是来自服务端的消息：HH:mm:ss"。
 * 停用原因：
 *   1. 这不是业务消息 —— 管理端 onmessage 里会 JSON.parse(msg.data)，
 *      而这句不是 JSON，每 5 秒就在浏览器控制台抛一次解析错误；
 *   2. 它和"来单提醒"抢同一条连接的写权限（BasicRemote.sendText 非线程安全），
 *      并发写会抛 TEXT_FULL_WRITING，导致真正的来单提醒被丢掉。
 *
 * 想确认连接是否活着，看后端日志里的"WebSocket 连接建立/断开"就够了，不需要定时刷屏。
 * 真要做心跳，建议 30 秒一次、并且只发 {"type":0} 这种合法 JSON，前端收到 type=0 直接忽略。
 */
@Component
public class WebSocketTask {

    @Autowired
    private WebSocketServer webSocketServer;

    // @Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        webSocketServer.sendToAllClient(
                "这是来自服务端的消息：" + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()));
    }
}
