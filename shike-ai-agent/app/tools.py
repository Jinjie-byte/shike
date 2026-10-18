"""Agent 的工具集（最终版：2 个工具）。

============================================================
设计说明
============================================================
只有两个工具：
  · get_menu()                          只读，一次返回全部在售【菜品 + 套餐】
  · add_to_cart(item_id, item_type)     写入（可撤销），加 1 份

为什么不做关键词检索（早期用过 search_dish，已删）：
    用户："想吃辣的" → 模型提炼 keyword="辣" → SQL LIKE '%辣%' → 0 条
    因为菜名和描述里根本没有"辣"字。错不在 SQL，错在"让模型去猜一个能命中库的词"。
    改成把菜单全量给模型 —— 它认识"剁椒""水煮"，自己就能判断。

为什么套餐也塞进 get_menu，而不是单开 get_setmeals：
    模型判断"该推菜还是推套餐"必须同时看到两者；分两个工具就要调两次，
    还可能漏调一次。工具越少，模型越稳。

为什么全量给还扛得住：在售菜品 20 多道 + 1 个套餐，JSON 约 1~2KB，
    单次调用不到 0.2 分钱。超过 100 道时就要换成"先按分类缩小范围"。
============================================================
"""

import json

import httpx
from langchain_core.tools import tool

from app.config import JAVA_BASE, HTTP_TIMEOUT, INTERNAL_TOKEN

# ★ trust_env=False —— 不走 HTTP_PROXY / HTTPS_PROXY 等环境变量。
#   为什么必须：本机常开着代理软件，它会把 127.0.0.1 的请求也劫持走，
#   结果服务明明在跑，请求却报 502（网关错误）。
#   这是本地联调最隐蔽的一个坑 —— 表现为"服务明明起了却连不上"。
_client = httpx.Client(trust_env=False, timeout=HTTP_TIMEOUT)

# 内部接口共享密钥（对应 Java 的 InternalTokenInterceptor）。
# 没配就不带头 —— 本地开发两边都不配，不校验。
_HEADERS = {"X-Internal-Token": INTERNAL_TOKEN} if INTERNAL_TOKEN else {}


@tool
def get_menu() -> str:
    """获取食刻完整在售菜单（所有菜品 + 所有套餐，含分类、价格、描述）。

    什么时候用：任何与菜品、套餐、口味、价格相关的问题都先调它 ——
      包括"有什么菜""想吃辣的""30块以内""有没有鱼""套餐里有什么"这类模糊需求。

    拿到结果后，你自己判断哪些符合用户要求：
      - 口味看 dishes[].description
        （"剁椒""水煮"通常是辣；"清炒""清蒸"通常清淡）
      - 预算按 price 过滤（菜品和套餐都要看）
      - 不要只按菜名匹配 —— 用户很少说出准确菜名
      - 用户问"套餐里有什么"时，看 setmeals[].items（每项有 name 和 copies）

    返回 JSON：
      {"dishCount": 24,
       "dishes":   [{"id":1,"name":"剁椒鱼头","price":66.0,
                     "description":"原料：鲢鱼，剁椒","categoryName":"川湘菜"}],
       "setmealCount": 1,
       "setmeals": [{"id":32,"name":"鸡你太美","price":50.0,"description":"真的很帅",
                     "items":[{"name":"鮰鱼2斤","copies":1}]}]}
    """
    try:
        resp = _client.get(f"{JAVA_BASE}/internal/ai/menu", headers=_HEADERS)
        resp.raise_for_status()
        body = resp.json()
        if body.get("code") != 1:
            return f"获取菜单失败：{body.get('msg')}"

        data = body.get("data") or {}
        dishes = data.get("dishes") or []
        setmeals = data.get("setmeals") or []

        return json.dumps(
            {
                "dishCount": len(dishes),
                "dishes": dishes,
                "setmealCount": len(setmeals),
                "setmeals": setmeals,
            },
            ensure_ascii=False,
        )
    except Exception as e:
        # ★ 工具内部出错时返回一句人话，不要抛异常 —— 抛出去整条链就断了
        return f"获取菜单失败：{e}"


def make_add_to_cart(user_id: int):
    """生成一个绑定了 user_id 的加购工具。

    为什么要用工厂函数：
      user_id 绝对不能让模型自己填（它会瞎编一个）。
      必须由后端从登录态里取出来、注入进闭包。
    """

    @tool
    def add_to_cart(item_id: int, item_type: str) -> str:
        """把一道菜或一个套餐加入当前用户的购物车（每次加 1 份）。

        什么时候用：只有当用户**明确**说要加购时才调 ——
          比如"帮我加一份""要这个""来一份"。

        绝对不要在用户只是挑选、犹豫、询问详情时调用。
        那种情况应该先在回复里问一句"要帮您加一份吗？"，等用户确认后再调。
        绝不要在同一次回复里既询问又执行。

        参数：
          item_id    必须从 get_menu 返回的 id 字段里取，不要自己编
          item_type  只能是 "dish"（菜品）或 "setmeal"（套餐）
                     —— 从 get_menu 里拿的 id 属于 dishes 就传 dish，
                        属于 setmeals 就传 setmeal
        """
        if item_type not in ("dish", "setmeal"):
            return f"加购失败：item_type 只能是 dish 或 setmeal，收到的是 {item_type}"

        params = {"userId": user_id}
        if item_type == "dish":
            params["dishId"] = item_id
        else:
            params["setmealId"] = item_id

        try:
            resp = _client.post(
                f"{JAVA_BASE}/internal/ai/cart/add", params=params, headers=_HEADERS
            )
            resp.raise_for_status()
            body = resp.json()
            if body.get("code") != 1:
                return f"加购失败：{body.get('msg')}"
            label = "菜品" if item_type == "dish" else "套餐"
            return f"已把{label}（id={item_id}）加入购物车。"
        except Exception as e:
            return f"加购失败：{e}"

    return add_to_cart


def build_tools(user_id: int):
    """按当前用户组装工具集 —— 每次请求调用一次。"""
    return [get_menu, make_add_to_cart(user_id)]
