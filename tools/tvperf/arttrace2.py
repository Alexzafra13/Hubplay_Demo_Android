r"""Tiempo inclusivo por método (sin contar recursión) del hilo principal en
un perfil de muestreo de ART (`am profile start --sampling 500 ...`).

Uso: python arttrace2.py p.trace <regex-incluir> [regex-excluir] [n]
Ej.: python arttrace2.py p.trace '^com\.alex\.hubplay' '' 30
"""
import struct,sys,collections,re
raw=open(sys.argv[1],'rb').read()
end=raw.index(b"*end\n")+5
hdr=raw[:end].decode('utf-8','replace')
threads={}; methods={}; sec=None
for line in hdr.splitlines():
    if line.startswith('*'): sec=line; continue
    p=line.split('\t')
    if sec=='*threads' and len(p)>=2: threads[int(p[0])]=p[1]
    elif sec=='*methods' and len(p)>=3: methods[int(p[0],16)]=p[1]+'.'+p[2]
b=raw[end:]
magic,ver,off,start=struct.unpack_from('<4sHHQ',b,0)
recsize=struct.unpack_from('<H',b,16)[0]
main=[t for t,n in threads.items() if n=='main'][0]
i=off; stack=[]; active=collections.Counter(); incl=collections.Counter()
pat=re.compile(sys.argv[2]) if len(sys.argv)>2 else None
excl=re.compile(sys.argv[3]) if len(sys.argv)>3 and sys.argv[3] else None
total=0; first=None; last=None
while i+recsize<=len(b):
    tid=struct.unpack_from('<H',b,i)[0]; mid=struct.unpack_from('<I',b,i+2)[0]; wall=struct.unpack_from('<I',b,i+10)[0]; i+=recsize
    if tid!=main: continue
    action=mid&3; m=mid&~3
    if first is None: first=wall
    last=wall
    if action==0:
        outer = active[m]==0
        active[m]+=1; stack.append((m,wall,outer))
    elif stack:
        m0,t0,outer=stack.pop(); active[m0]-=1
        if outer: incl[m0]+=wall-t0
name=lambda m: methods.get(m,hex(m))
print("ventana %.0f ms"%((last-first)/1000))
rows=[(d,name(m)) for m,d in incl.items() if (not pat or pat.search(name(m))) and not (excl and excl.search(name(m)))]
rows.sort(reverse=True)
for d,n in rows[:int(sys.argv[4]) if len(sys.argv)>4 else 50]: print("%8.1f  %s"%(d/1000,n[:120]))
