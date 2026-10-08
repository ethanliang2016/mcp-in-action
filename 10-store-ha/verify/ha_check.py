"""
篇十高可用实测脚本（走 nginx :8080 验证完整链路）。

用法（容器起来后）：
  python ha_check.py poll       # 多实例轮询：连续 save_context，看"处理实例=port"是否分散
  python ha_check.py normal     # 正常：get_device_status / get_store_sales 应正常返回
  python ha_check.py circuit    # 故障演练：HA_DEVICE_LATENCY_MS=5000 起实例后，连续调设备状态应转"熔断降级"
  python ha_check.py sentinel   # 哨兵切换后：stop redis-master，save/load_context 仍应成功（连到新 master）
"""
import asyncio
import sys

from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

URL = "http://localhost:8080/mcp"
MODE = sys.argv[1] if len(sys.argv) > 1 else "poll"


async def call(session: ClientSession, name: str, args: dict) -> str:
    r = await session.call_tool(name, args)
    if r.content:
        return "".join(getattr(c, "text", "") for c in r.content)
    return str(r)


async def main():
    async with streamable_http_client(URL) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            if MODE == "poll":
                for i in range(6):
                    t = await call(session, "save_context", {"key": f"k{i}", "value": f"v{i}"})
                    print(f"[{i}] {t}")

            elif MODE == "normal":
                print("device:", await call(session, "get_device_status", {"storeCode": "ST001"}))
                print("sales :", await call(session, "get_store_sales", {"storeCode": "ST001"}))

            elif MODE == "circuit":
                # 故障演练：下游慢(>1s) → 慢调用率超50% → 熔断打开 → 返回"暂不可用"
                for i in range(12):
                    t = await call(session, "get_device_status", {"storeCode": "ST001"})
                    print(f"[{i}] {t}")

            elif MODE == "sentinel":
                print("save:", await call(session, "save_context", {"key": "sentinel_test", "value": "ok"}))
                print("load:", await call(session, "load_context", {"key": "sentinel_test"}))


asyncio.run(main())
