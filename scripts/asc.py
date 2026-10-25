"""Minimal App Store Connect API client: ES256 JWT via openssl, no third-party packages. Prints no secrets."""
import base64, json, os, subprocess, sys, time, urllib.request, urllib.error

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

def der_to_raw(der):
    # SEQUENCE { INTEGER r, INTEGER s } -> r||s, 32 bytes each
    i = 2 if der[1] < 0x80 else 2 + (der[1] & 0x7F)
    def read_int(i):
        assert der[i] == 0x02
        n = der[i + 1]; v = der[i + 2:i + 2 + n]
        return v.lstrip(b"\x00").rjust(32, b"\x00"), i + 2 + n
    r, i = read_int(i); s, _ = read_int(i)
    return r + s

def token():
    kid, iss = os.environ["ASC_KEY_ID"], os.environ["ASC_ISSUER_ID"]
    key = os.environ.get("ASC_KEY_PATH") or os.path.expanduser(f"~/.appstoreconnect/private_keys/AuthKey_{kid}.p8")
    head = b64(json.dumps({"alg": "ES256", "kid": kid, "typ": "JWT"}).encode())
    now = int(time.time())
    body = b64(json.dumps({"iss": iss, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}).encode())
    msg = f"{head}.{body}".encode()
    der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key], input=msg, capture_output=True, check=True).stdout
    return f"{head}.{body}.{b64(der_to_raw(der))}"

def call(method, path, data=None):
    req = urllib.request.Request("https://api.appstoreconnect.apple.com" + path, method=method,
                                 data=json.dumps(data).encode() if data else None,
                                 headers={"Authorization": "Bearer " + token(), "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")

if __name__ == "__main__":
    method, path = sys.argv[1], sys.argv[2]
    data = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None
    status, out = call(method, path, data)
    print(status); print(json.dumps(out, indent=1)[:3000])
