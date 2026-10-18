"""第 1 步：环境自检。直接运行，不需要改任何东西。

检查三件事：Python 版本 / 依赖是否装齐 / DeepSeek Key 是否设了。
三项全通过，才往下走。

运行：
    .venv/Scripts/python.exe step1_check.py
"""

import os
import sys
from pathlib import Path


def main():
    ok = True

    # ---- 0. 解释器自证 ----
    # 为什么要有这一步：机器上往往装了不止一个 Python，
    # 用错了那个会报出"包没装"的假象（其实只是没装在它肚子里），
    # 后面每一步都会踩。先确认自己是谁，比什么都重要。
    expect_venv = Path(__file__).resolve().parent / ".venv"
    cur_prefix = Path(sys.prefix).resolve()
    in_venv = os.path.normcase(str(cur_prefix)) == os.path.normcase(str(expect_venv))

    print(f"[0] 解释器    : {sys.executable}")
    if in_venv:
        print("                -> 正确（本项目的 .venv）")
    else:
        print("                -> ★ 错了！这不是本项目的 .venv")
        print(f"                ★ 应该用：{expect_venv / 'Scripts' / 'python.exe'}")
        print("                ★ 或者先激活：.venv\\Scripts\\activate")
        ok = False
    print()

    # ---- 1. Python 版本 ----
    v = sys.version_info
    line = f"[1] Python 版本 : {v.major}.{v.minor}.{v.micro}"
    if v >= (3, 10):
        print(line, " -> 通过")
    else:
        print(line, " -> 不合格（需要 3.10 以上）")
        ok = False

    # ---- 2. 依赖 ----
    need = [
        ("fastapi", "fastapi"),
        ("uvicorn", "uvicorn"),
        ("langgraph", "langgraph"),
        ("langchain_deepseek", "langchain-deepseek"),
        ("openai", "openai"),
        ("httpx", "httpx"),
    ]
    print("[2] 依赖检查 :")
    for module_name, package_name in need:
        try:
            __import__(module_name)
            print(f"        {package_name:<20} 已装")
        except ImportError:
            print(f"        {package_name:<20} 未装   <-- 需要 pip install")
            ok = False

    # ---- 3. API Key ----
    key = os.getenv("DEEPSEEK_API_KEY", "")
    print("[3] API Key   :", end=" ")
    if not key:
        print("未设置   <-- 需要设环境变量 DEEPSEEK_API_KEY")
        ok = False
    elif not key.startswith("sk-"):
        print(f"已设置，但格式可疑（应以 sk- 开头，当前长度 {len(key)}）")
        ok = False
    else:
        # 只打印前 6 位，后面的不显示（避免密钥被人看到）
        print(f"已设置（长度 {len(key)}，形如 {key[:6]}...）")

    print()
    if ok:
        print("结论：全部通过，可以进入第 2 步。")
    else:
        print("结论：有项目未通过，先解决上面标了 <-- 的地方。")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
