"""第 3 步：让模型学会"调工具"—— 本课的核心。

这一步专门验证一件事：
    模型会不会在需要的时候，主动去调用你写的那个函数？

为什么用假数据？
    因为这样你一眼就能看出"这条回答是查来的，不是模型自己编的"。
    课 2 会把这个假菜单换成真调用 Java 接口。

运行：
    .venv/Scripts/python.exe step3_tool_demo.py

============================================================
要你填 3 个空。
============================================================
"""

from langchain.agents import create_agent
from langchain_core.tools import tool
from langchain_deepseek import ChatDeepSeek


# 我们自己造的假菜单。注意：里面没有"波士顿龙虾"，
# 待会儿用它来验证模型是"真去查了"还是"张口就编"。
FAKE_MENU = {
    "剁椒鱼头": "66.00",
    "蜀味水煮草鱼": "38.00",
    "清炒小油菜": "18.00",
}


# ------------------------------------------------------------------
# 空①：给这个工具写 docstring。
#
#   ★ 这段文字不是写给人看的，是写给【模型】看的。
#     模型靠它判断两件事：什么时候该用这个工具、参数该怎么填。
#     写糊了有两个后果：模型压根不调它；或者调了但参数乱填。
#
#   至少要说清：这个工具是干什么的 + dish_name 该传什么。
# ------------------------------------------------------------------
@tool
def get_dish_price(dish_name: str) -> str:
    """查询菜品价格，dish_name传入菜品名称"""
    price = FAKE_MENU.get(dish_name)
    if price is None:
        return f"菜单里没有「{dish_name}」这道菜。"
    return f"{dish_name} 的价格是 {price} 元。"


def main():
    # temperature=0：让输出稳定。点单场景要的是"别乱说"，不是创造力。
    model = ChatDeepSeek(model="deepseek-flash", temperature=0)

    # --------------------------------------------------------------
    # 空②：把上面那个工具交给 Agent。
    #   create_agent 的第二个参数是"工具列表"。
    #   提示：列表里放的是【函数本身】，不是函数名的字符串、也不是调用结果。
    # --------------------------------------------------------------
    agent = create_agent(model, tools=[get_dish_price])

    print("--- 第一问：菜单里有的菜 ---")
    question = "剁椒鱼头多少钱？"
    print("我：", question)
    result = agent.invoke({"messages": [("user", question)]})
    print(result)
    # --------------------------------------------------------------
    # 空③：把模型的最终回答打印出来。
    #   result 是个字典；"messages" 里装着整轮对话的消息列表，
    #   最后一条 [-1] 就是答案，用 .content 取文本。
    # --------------------------------------------------------------
    print("AI：", result["messages"][-1].content)

    print()
    print("--- 第二问：菜单里【没有】的菜（验证它会不会编）---")
    question2 = "你们有波士顿龙虾吗？"
    print("我：", question2)
    result2 = agent.invoke({"messages": [("user", question2)]})
    print("AI：", result2["messages"][-1].content)


if __name__ == "__main__":
    main()
