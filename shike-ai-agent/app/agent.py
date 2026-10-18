"""组装 Agent：把模型、工具、提示词、记忆绑在一起。"""

from langchain.agents import create_agent
from langchain_deepseek import ChatDeepSeek
from langgraph.checkpoint.memory import MemorySaver

from app.tools import build_tools

# ============================================================
# 多轮记忆
# ============================================================
# MemorySaver 把对话历史存在【进程内存】里：
#   · 零依赖、开发阶段够用
#   · 进程重启就丢 —— 上线要换成 Redis 版（项目本来就有 Redis）
#
# ★ 必须是模块级单例，不能放在 build_agent 里 new。
#   因为 build_agent 每次请求都会调用一次，每 new 一个就等于把历史清零。
#
# ★ 它存的不是"某个用户的历史"，而是"所有 thread_id 的历史"。
#   具体取哪一份，由调用时传的 config={"configurable": {"thread_id": ...}} 决定。
#   类比 SQL：MemorySaver 是那张表，thread_id 是 WHERE 条件。
_CHECKPOINTER = MemorySaver()


SYSTEM_PROMPT = """你是「食刻外卖」的点单助手。

【必须遵守】
用户的每一次需求，都必须先调用 get_menu 工具拿到完整菜单（菜品 + 套餐），再基于菜单内容回答。
绝对不要凭想象编造菜名、套餐名和价格 —— 菜单里没有的就不要提。

【怎么选】
按用户说的口味（看 description）、预算（看 price）自己判断，
不要只按菜名做字面匹配 —— 用户很少会说出准确的菜名。
用户问"套餐里有什么"时，看 setmeals 里每一项的 items。

【关于加购物车】
只有当用户**明确**说要加购时（"帮我加一份""要这个""来一份"），才调用 add_to_cart。
如果用户只是在挑选、犹豫、询问（"这个怎么样""好吃吗"），
先在回复里问一句"要帮您加一份吗？"，等用户确认。
绝不要在同一次回复里既询问又执行。
调用 add_to_cart 时，item_id 必须从 get_menu 返回的 id 里取；
菜品传 item_type="dish"，套餐传 item_type="setmeal"。

【多轮对话】
你可以看到本会话之前的对话。用户说"再便宜点""换一个""就要第二个"时，
直接基于上文回答，不需要重新介绍自己。

【回答风格】
简短。列出菜名/套餐名和价格，最后可以给一句推荐理由。
★ 用纯文本回答，不要用 Markdown 语法（不要 **加粗**、# 标题、表格），
  每道菜单独一行，用「-」开头即可。"""


def build_agent(user_id: int):
    """按用户组装 Agent。

    为什么按 user_id 组装：add_to_cart 的 user_id 必须由后端注入（不能给模型看），
    所以工具集是"每个用户一份"的。
    """
    model = ChatDeepSeek(model="deepseek-flash", temperature=0)
    return create_agent(
        model,
        tools=build_tools(user_id),
        system_prompt=SYSTEM_PROMPT,
        checkpointer=_CHECKPOINTER,
    )
