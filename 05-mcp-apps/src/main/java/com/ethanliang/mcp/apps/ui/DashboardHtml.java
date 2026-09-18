package com.ethanliang.mcp.apps.ui;

/**
 * 界面模板——就是 ui:// 资源返回的那段 HTML。
 *
 * 两条硬约束，违反了界面在宿主里就是一块白板：
 *
 * 1. **自包含**：CSS / JS 全部内联，不引任何 CDN。
 *    宿主在 deny-by-default 的 CSP 下渲染，外链脚本默认被拦掉。
 * 2. **只渲染、不直连**：界面不自己访问后端，
 *    所有交互走 postMessage 上的 JSON-RPC，由宿主代理回服务端。
 *    宿主负责决定哪些调用放行——界面上有个按钮，不等于模型就有了权限。
 */
public final class DashboardHtml {

    public static final String TEMPLATE = """
            <!doctype html>
            <html lang="zh">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{margin:0;font:14px/1.5 system-ui,sans-serif;background:#0f1115;color:#e6e6e6}
              .wrap{padding:16px}
              h2{margin:0 0 12px;font-size:15px;font-weight:600}
              .env{margin-bottom:14px;color:#9aa4b2;font-size:12px}
              .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(130px,1fr));gap:10px}
              .card{border:1px solid #232833;border-radius:10px;padding:10px;background:#161a21}
              .name{font-size:13px;margin-bottom:8px}
              .btn{width:100%;border:0;border-radius:6px;padding:6px 0;cursor:pointer;font-size:12px}
              .on{background:#1f6f4a;color:#eafff4}
              .off{background:#3a2027;color:#ffe9ee}
              .hint{margin-top:12px;font-size:12px;color:#6b7480}
            </style>
            </head>
            <body>
            <div class="wrap">
              <h2>机房面板</h2>
              <div class="env" id="env">等待数据…</div>
              <div class="grid" id="grid"></div>
              <div class="hint" id="hint">界面动作经宿主代理回服务端，不直连后端</div>
            </div>
            <script>
              var seq = 0, waiting = new Map();

              // 界面 -> 宿主：postMessage 上的 MCP 风格 JSON-RPC
              function callTool(name, args) {
                var id = ++seq;
                return new Promise(function (resolve, reject) {
                  waiting.set(id, { resolve: resolve, reject: reject });
                  parent.postMessage({
                    jsonrpc: '2.0', id: id, method: 'tools/call',
                    params: { name: name, arguments: args }
                  }, '*');
                });
              }

              function render(data) {
                if (!data) return;
                document.getElementById('env').textContent =
                  '温度 ' + data.temperature + '℃ · 湿度 ' + data.humidity + '%';
                var grid = document.getElementById('grid');
                grid.innerHTML = '';
                (data.devices || []).forEach(function (d) {
                  var card = document.createElement('div');
                  card.className = 'card';

                  var nameEl = document.createElement('div');
                  nameEl.className = 'name';
                  nameEl.textContent = d.name;

                  var btn = document.createElement('button');
                  btn.className = 'btn ' + (d.on ? 'on' : 'off');
                  btn.textContent = d.on ? '运行中 · 点击关闭' : '已关闭 · 点击启动';
                  btn.onclick = function () {
                    callTool('control_device', { deviceName: d.name, operateType: d.on ? 0 : 1 })
                      .then(function () {
                        document.getElementById('hint').textContent = '已提交：' + d.name;
                      })
                      .catch(function () {
                        document.getElementById('hint').textContent = '调用被宿主拒绝';
                      });
                  };

                  card.appendChild(nameEl);
                  card.appendChild(btn);
                  grid.appendChild(card);
                });
              }

              // 宿主 -> 界面：工具结果下发；structuredContent 优先，没有就退回文本
              window.addEventListener('message', function (e) {
                var msg = e.data;
                if (!msg) return;
                if (msg.id && waiting.has(msg.id)) {
                  var w = waiting.get(msg.id);
                  waiting.delete(msg.id);
                  msg.error ? w.reject(msg.error) : w.resolve(msg.result);
                  return;
                }
                var payload = msg.result || msg.params || msg;
                var sc = payload.structuredContent || payload;
                if (sc && (sc.devices || sc.temperature)) render(sc);
              });
            </script>
            </body>
            </html>
            """;

    private DashboardHtml() {
    }
}
