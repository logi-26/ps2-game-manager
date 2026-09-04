"""
Exercises the oplpops-server wire protocol without the GUI.
Start the server first (local-dev/run-server.ps1), then:  python local-dev/protocol-check.py

Protocol (see oplpops-server/src/src/tcpserver/WorkerRunnable.java):
  request : int32 big-endian length, then ASCII  "CMD,arg1,arg2,..."
            (uploads also append raw file bytes after a "NN,CMD,..." header)
  reply   : int32 big-endian length, then that many bytes
            - a file, or an ASCII sentinel like NO_IMAGE / NO_CONFIG / RESPONSE
"""
import socket
import struct
import sys

HOST, PORT = "127.0.0.1", 6789
MAC, VER, OS = "AA-BB-CC-DD-EE-FF", "0.6.1", "Windows_amd64"


def call(msg: str, extra: bytes = b"") -> tuple[int, bytes]:
    with socket.create_connection((HOST, PORT), timeout=5) as s:
        s.sendall(struct.pack(">i", len(msg)) + msg.encode() + extra)
        (n,) = struct.unpack(">i", _recvn(s, 4))
        return n, _recvn(s, n)


def _recvn(s: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = s.recv(min(65536, n - len(buf)))
        if not chunk:
            break
        buf += chunk
    return buf


TESTS = [
    ("ART (hit)",     f"ART,PS2,NTSCU,_COV,SLUS_207.68,_(0),{MAC},{VER},{OS},0"),
    ("ART (miss)",    f"ART,PS2,NTSCU,_COV,SLUS_999.99,_(0),{MAC},{VER},{OS},0"),
    ("ART_NUM",       f"ART_NUM,PS2,NTSCU,_COV,SLUS_207.68,{MAC},{VER},{OS},0"),
    ("CONFIG",        f"CONFIG,PS2,NTSCU,SLUS_207.68,{MAC},{VER},{OS},0"),
    ("CHEAT",         f"CHEAT,PS2,PAL,SCES_509.24,{MAC},{VER},{OS},0"),
    ("ART_LIST",      f"ART_LIST,PS2,{MAC},{VER},{OS},0"),
    ("VERSION",       "VERSION,0"),
    ("RESPOND",       "RESPOND,0"),
]

try:
    for label, msg in TESTS:
        n, data = call(msg)
        head = data[:40].decode("latin1").replace("\n", "\\n")
        print(f"  {label:12} -> {n:>7} bytes | {head!r}")
    print("\nOK")
except OSError as e:
    print(f"FAILED to reach {HOST}:{PORT} - is the server running?  ({e})")
    sys.exit(1)
