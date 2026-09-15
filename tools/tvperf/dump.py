"""Lista los nodos del volcado de uiautomator: '*' marca el que tiene el foco.

Uso: python dump.py ui.xml [focus]
"""
import io
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
xml = open(sys.argv[1], encoding="utf-8").read()
only_focus = len(sys.argv) > 2 and sys.argv[2] == "focus"


def attr(node, key):
    m = re.search(" " + key + r'="([^"]*)"', node)
    return m.group(1) if m else ""


for m in re.finditer(r"<node[^>]*>", xml):
    n = m.group(0)
    focused = attr(n, "focused") == "true"
    if only_focus and not focused:
        continue
    if attr(n, "text") or attr(n, "content-desc") or focused:
        print("*" if focused else " ", attr(n, "bounds"), repr(attr(n, "text"))[:50], repr(attr(n, "content-desc"))[:40])
