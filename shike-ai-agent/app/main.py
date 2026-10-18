"""FastAPI 入口：把 Agent 暴露成一个 HTTP 接口。

启动（在 shike-ai-agent 目录下执行）：
    .venv/Scripts/python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000

调用示例（Java 转发时就是这么调的）：
    curl -X POST http://127.0.0.1:8000/chat \
      -H "Content-Type: application/json" \
      -d '{"messages":[{"role":"user","content":"想吃辣的"}],"user_id":2,"session_id":"abc"}'

★ 多轮记忆的关键：
    同一个 session_id 的请求，会自动接上之前的对话历史（由 checkpointer 维护）。
    所以前端【只需要传最新一条用户消息】，不要重复传全部历史 ——
    传了反而会重复累积：历史既在 checkpointer 里，又在 messages 里。
"""

from fastapi import FastAPI
from pydantic import BaseModel

from app.agent import build_agent

app = FastAPI()

# 按用户缓存 Agent 实例。
# 为什么要缓存：build_agent 会创建模型客户端和工具闭包，每次请求重建是浪费；
# 更重要的一点 —— 同一个用户复用同一个 agent，记忆的 thread_id 才对得上。
_agents: dict[int, object] = {}


def get_agent(user_id: int):
    agent = _agents.get(user_id)
    if agent is None:
        agent = build_agent(user_id)
        _agents[user_id] = agent
    return agent


class ChatRequest(BaseModel):
    messages: list[dict]

    # Java 转发时会把【当前登录用户】带过来。
    # 为什么要有这个：AI 加购物车必须知道"以谁的身份加"，
    # 而这个信息不能让模型自己说 —— 只能由后端从登录态里取。
    user_id: int = 0

    # 会话 id。同一个 id = 同一段记忆。
    # 为空时退化成"每个用户一个会话"，不会报错。
    session_id: str = ""


@app.get("/health")
def health():
    return {"ok": True, "cachedAgents": len(_agents)}


@app.post("/chat")
async def chat(req: ChatRequest):
    if not req.messages:
        return {"reply": "您想吃什么？可以直接说口味和预算，比如「想吃辣的，30块以内」。"}

    agent = get_agent(req.user_id or 0)

    # thread_id 就是"这段记忆归谁"。为空时按用户退化成单会话。
    thread_id = req.session_id or f"user-{req.user_id or 0}"
    config = {"configurable": {"thread_id": thread_id}}

    result = await agent.ainvoke({"messages": req.messages}, config)
    return {"reply": result["messages"][-1].content, "session_id": thread_id}


@app.post("/chat/reset")
def reset(req: ChatRequest):
    """清空某个会话的记忆（前端「新对话」按钮用）。"""
    thread_id = req.session_id or f"user-{req.user_id or 0}"
    try:
        from app.agent import _CHECKPOINTER

        _CHECKPOINTER.delete_thread(thread_id)
        return {"ok": True, "session_id": thread_id}
    except Exception as e:
        # 删不掉也不算失败 —— 前端换个新 session_id 效果一样
        return {"ok": False, "error": str(e), "session_id": thread_id}
