import json
import re
from http.server import BaseHTTPRequestHandler, HTTPServer

SEEN = []


class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(n)
        auth = self.headers.get("Authorization", "")
        ctype = self.headers.get("Content-Type", "")
        SEEN.append((self.path, auth, ctype, len(body)))

        if self.path == "/v1/audio/transcriptions":
            # 校验 multipart 里有 model / file 字段，且是合法 WAV
            ok = b'name="model"' in body and b'name="file"' in body and b"RIFF" in body
            if not ok:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b'{"error":"bad multipart"}')
                return
            out = json.dumps({"text": "Hello world, this is a test."}).encode()
        elif self.path == "/v1/chat/completions":
            req = json.loads(body.decode())
            assert req["model"]
            assert req["messages"][1]["content"]
            out = json.dumps({"choices": [{"message": {"content": "你好世界，这是一次测试。"}}]}).encode()
        else:
            self.send_response(404)
            self.end_headers()
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    srv = HTTPServer(("127.0.0.1", 8765), H)
    print("mock up", flush=True)
    srv.serve_forever()
