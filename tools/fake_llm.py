"""本地假 LLM 服务：给 M3 段落翻译做端到端验收用，不连外网、不花钱。

    python tools/fake_llm.py [port] [--drop N] [--delay S] [--min-bytes N]

- --drop N       故意漏掉编号 N 的段落，验证「只补发缺的那几段」
- --delay S      每次响应前睡 S 秒，验证「翻译中」状态和取消
- --min-bytes N  用 padding 把响应体撑到至少 N 字节（默认 1500）。
                 真实厂商的响应有 id/model/usage 等字段，动辄 400+ 字节；
                 假服务如果回一个几十字节的迷你 JSON，会掩盖「客户端截断响应」这类 bug。
                 曾经就是因为它太短，`readAll` 里一个 300 字符的截断一直没被测出来。

每个请求打印一行到 stdout，验收时数行数就能证明有没有重复请求。
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 够用的假译法：不动原文结构，只加前缀，方便肉眼确认哪段对应哪段
PREFIX = "【译】"


def translate_lines(user_text, drop):
    out = []
    for line in user_text.splitlines():
        if "|" not in line:
            continue
        num, text = line.split("|", 1)
        num = num.strip()
        if drop and num == str(drop):
            continue  # 模拟模型漏段
        out.append(f"{num}|{PREFIX}{text.strip()}")
    return "\n".join(out)


class Handler(BaseHTTPRequestHandler):
    drop = 0
    delay = 0.0
    min_bytes = 1500

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        user = ""
        for m in body.get("messages", []):
            if m.get("role") == "user":
                user = m.get("content", "")
        auth = self.headers.get("Authorization", "")
        thinking = body.get("thinking")
        print(f"REQ path={self.path} model={body.get('model')} "
              f"paras={len(user.splitlines())} auth={auth[:12]} thinking={thinking}",
              flush=True)

        if self.delay:
            time.sleep(self.delay)

        content = translate_lines(user, self.drop)
        payload = {
            "id": "chatcmpl-fake-0000000000000000",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": body.get("model") or "fake",
            "choices": [{
                "index": 0,
                "message": {"role": "assistant", "content": content},
                "logprobs": None,
                "finish_reason": "stop",
            }],
            "usage": {
                "prompt_tokens": len(user) // 4,
                "completion_tokens": len(content) // 4,
                "total_tokens": (len(user) + len(content)) // 4,
                "prompt_cache_hit_tokens": 0,
                "prompt_cache_miss_tokens": len(user) // 4,
            },
            "system_fingerprint": "fp_fake_0000",
        }
        if self.min_bytes:
            # 用真实响应里也存在的最长字段把体积撑到典型大小，
            # 让「客户端只读前 N 字节」这类 bug 一定会现形
            payload["service_tier"] = "default"
            payload["padding"] = "x" * self.min_bytes

        data = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass  # 只留自己的 REQ 行


def main():
    args = sys.argv[1:]
    port = 8787
    if args and args[0].isdigit():
        port = int(args.pop(0))
    if "--drop" in args:
        Handler.drop = int(args[args.index("--drop") + 1])
    if "--delay" in args:
        Handler.delay = float(args[args.index("--delay") + 1])
    if "--min-bytes" in args:
        Handler.min_bytes = int(args[args.index("--min-bytes") + 1])

    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"fake LLM on :{port} drop={Handler.drop} delay={Handler.delay} "
          f"min_bytes={Handler.min_bytes}", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
