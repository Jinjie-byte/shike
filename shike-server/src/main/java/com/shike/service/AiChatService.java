package com.shike.service;

import java.util.List;
import java.util.Map;

/**
 * AI 对话服务：把前端的对话转发给 Python 侧的 Agent，拿回回答。
 */
public interface AiChatService {

    /**
     * @param messages  本轮要发给模型的对话（前端只传最新一条，历史由 Python 侧记忆维护）
     * @param sessionId 会话 id，用于服务端多轮记忆；可为 null
     * @return 模型的回答文本
     */
    String chat(List<Map<String, Object>> messages, String sessionId);
}
