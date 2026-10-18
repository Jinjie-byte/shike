"""集中放可变配置 —— 不写死在代码里。

为什么单独一个文件：
  这些值将来部署到服务器会变（Java 地址、超时时间），
  散落在各处的话，改起来容易漏。
"""

import os

# Java 后端地址。Java 和 Python 同机部署，所以是回环地址。
JAVA_BASE = os.getenv("JAVA_BASE", "http://127.0.0.1:8080")

# 调 Java 接口的超时（秒）。这个是内部查询，不该超过 10 秒。
HTTP_TIMEOUT = float(os.getenv("AGENT_HTTP_TIMEOUT", "10"))

# 内部接口共享密钥 —— 对应 Java 侧的 shike.internal.token。
# 两边都配了同一个值才生效；都不配 = 不校验（本地开发就是这种情况）。
# 生产环境：在服务器上设环境变量 INTERNAL_TOKEN=xxx，
#           同时 application-prod.yml 里 shike.internal.token 填同一个值。
INTERNAL_TOKEN = os.getenv("INTERNAL_TOKEN", "")
