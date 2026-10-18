"""第 2 步：让模型开口说话（还不带工具）。

这一步只有一个目标：验证你的 Key 能调通 DeepSeek。
它不通，后面全都不用试 —— 所以先单独把它跑通，别急着加东西。

运行：
    .venv/Scripts/python.exe step2_hello.py

============================================================
要你填 2 个空。填完保存，再运行。
============================================================
"""

import os

from openai import OpenAI


# ------------------------------------------------------------------
# 空①：DeepSeek 的接口地址。
#   DeepSeek 的接口格式和 OpenAI 兼容，所以只要把 base_url 换成它自己的域名。
#   提示：教材《模块7》的 3.3 / 3.4 节，或官网 api-docs.deepseek.com
# ------------------------------------------------------------------
BASE_URL = "https://api.deepseek.com"


# ------------------------------------------------------------------
# 空②：模型名。
#   注意：网上大部分教程写的 `deepseek-chat`，在官方文档的模型列表里已经查不到了。
#   （它现在仍能调通，但没有文档背书的模型名随时会失效 —— 要用文档主推的那个。）
#   提示：教材 3.4 节专门标了这个坑。
# ------------------------------------------------------------------
MODEL = "deepseek-chat"


def main():
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        print("没读到 DEEPSEEK_API_KEY，先把环境变量设好再运行")
        return

    client = OpenAI(api_key=api_key, base_url=BASE_URL)

    resp = client.chat.completions.create(
        model=MODEL,
        messages=[{"role": "user", "content": "用一句话介绍一下你自己"}],
    )
    print("模型说：", resp.choices[0].message.content)


if __name__ == "__main__":
    main()
