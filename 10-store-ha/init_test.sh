#!/bin/sh
echo "=== direct to 8088 (bypass nginx) ==="
curl -s -i -X POST http://localhost:8088/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'
echo
echo "=== also try 2025-06-18 ==="
curl -s -i -X POST http://localhost:8088/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"clientInfo":{"name":"curl","version":"1.0"}}}'
