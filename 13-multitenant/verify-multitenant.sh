#!/usr/bin/env bash
# 篇13 多租户隔离 · 四件套验证脚本
# 在已启动的环境运行（Git Bash / WSL / Linux）
# 前置：docker compose up -d 已起 gw-1(8080) gw-2(8081) redis
#
# 端口可用环境变量覆盖（本机 8080 被占用时）：
#   GW1=http://localhost:18080 GW2=http://localhost:18081 ./verify-multitenant.sh

set -u
GW1="${GW1:-http://localhost:8080}"
GW2="${GW2:-http://localhost:8081}"
KEY_STORE="${KEY_STORE:-sk-store-0000000000000000000000000000}"
KEY_SUPPLY="${KEY_SUPPLY:-sk-supply-1111111111111111111111111111}"
KEY_FIN="${KEY_FIN:-sk-finance-2222222222222222222222222222}"
TOOL_STORE="${TOOL_STORE:-get_store_sales}"
TOOL_FIN="${TOOL_FIN:-gen_finance_report}"
CT="Content-Type: application/json"

tmp=$(mktemp -d)
echo "{\"tool\":\"$TOOL_STORE\"}" > "$tmp/store.json"
echo "{\"tool\":\"$TOOL_FIN\"}"   > "$tmp/fin.json"

echo "==== 1) 租户路由：缺失/非法 Key 应 401 ===="
curl -s -o /dev/null -w "  no-key  -> %{http_code}  (期望401)`n" "$GW1/mcp"
curl -s -o /dev/null -w "  bad-key -> %{http_code}  (期望401)`n" -H "X-Api-Key: sk-invalid" "$GW1/mcp"
curl -s -o /dev/null -w "  store   -> %{http_code}  (期望200)`n" -H "X-Api-Key: $KEY_STORE" "$GW1/mcp"

echo
echo "==== 2) 目录隔离：看不见就点不到 ===="
echo "  store   目录: $(curl -s -H "X-Api-Key: $KEY_STORE" "$GW1/tools/list")"
echo "  finance 目录: $(curl -s -H "X-Api-Key: $KEY_FIN"   "$GW1/tools/list")"
curl -s -H "X-Api-Key: $KEY_STORE" "$GW1/tools/list" | grep -q "$TOOL_FIN" \
  && echo "  FAIL: 门店能看到财务工具" || echo "  PASS: 门店目录已过滤（不含 $TOOL_FIN）"

echo
echo "==== 3) 凭证隔离：跨租户调工具应 403 ===="
curl -s -o /dev/null -w "  supply->$TOOL_STORE -> %{http_code}  (期望403)`n" \
  -H "X-Api-Key: $KEY_SUPPLY" -H "$CT" -d "@$tmp/store.json" "$GW1/mcp"

echo
echo "==== 4) 配额跨副本一致性：GW1 打满，GW2 同样 429 ===="
for i in $(seq 1 23); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "X-Api-Key: $KEY_STORE" \
    -H "$CT" -d "@$tmp/store.json" "$GW1/mcp")
  printf "  GW1 hit %2s -> %s\n" "$i" "$code"
done
echo "  -- 切到 GW2（计数在 Redis，应同样被限）--"
curl -s -o /dev/null -w "  GW2 store -> %{http_code}  (期望429)`n" \
  -H "X-Api-Key: $KEY_STORE" -H "$CT" -d "@$tmp/store.json" "$GW2/mcp"

echo
echo "==== 5) 故障演练：Redis 挂掉，按租户分级降级 ===="
echo "  （手动：docker compose stop redis）"
echo "  核心租户 fail-closed -> 期望 429 全拒："
curl -s -o /dev/null -w "    finance(closed) -> %{http_code}`n" \
  -H "X-Api-Key: $KEY_FIN" -H "$CT" -d "@$tmp/fin.json" "$GW1/mcp"
echo "  长尾租户 fail-open   -> 期望 200 放行："
curl -s -o /dev/null -w "    supply(open)    -> %{http_code}`n" \
  -H "X-Api-Key: $KEY_SUPPLY" -H "$CT" -d "@$tmp/store.json" "$GW1/mcp"
echo "  （恢复：docker compose start redis，再跑一次应回到 200/429 正常态）"

rm -rf "$tmp"
