package com.shike.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户端 AI 对话请求。
 *
 * messages 的结构由前端决定，形如：
 *   [{"role":"user","content":"想吃辣的"},
 *    {"role":"assistant","content":"..."},
 *    {"role":"user","content":"再便宜点"}]
 *
 * 为什么用 List&lt;Map&gt; 而不是定义强类型：
 *   消息格式是 OpenAI / DeepSeek 的通用协议（role + content），
 *   Java 这边只是"搬运工"，不需要理解每个字段。
 */
@Data
public class AiChatRequestDTO {

    private List<Map<String, Object>> messages;

    /**
     * 会话 id —— 多轮记忆的"主键"。
     *
     * 为什么需要：Python 侧用 LangGraph 的 checkpointer 按 sessionId 存历史。
     *   前端第一次进入 AI 页时生成一个随机串（存 localStorage），之后每次带上，
     *   服务端就能把"这一轮"接到"上一轮"后面 —— 用户说"再便宜点"才接得上话。
     *
     * 为空时 Python 会退化成"按用户 id 一个会话"，不会报错。
     */
    private String sessionId;
}
