import struct, zlib, os

def make_png(path, size=72):
    # 渐变色图标 (青色到深蓝)
    raw = b''
    for y in range(size):
        raw += b'\x00'
        for x in range(size):
            t = y / size
            r = int(0 + (26 - 0) * t)
            g = int(212 + (32 - 212) * t)
            b = int(255 + (50 - 255) * t)
            raw += bytes([r, g, b])
    def chunk(typ, data):
        crc_val = zlib.crc32(typ + data) & 0xffffffff
        return struct.pack('>I', len(data)) + typ + data + struct.pack('>I', crc_val)
    sig = b'\x89PNG\r\n\x1a\n'
    ihdr = struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)
    idat = zlib.compress(raw)
    with open(path, 'wb') as f:
        f.write(sig + chunk(b'IHDR', ihdr) + chunk(b'IDAT', idat) + chunk(b'IEND', b''))

base = '/root/MobileBalance/app/src/main/res'
make_png(os.path.join(base, 'mipmap-mdpi/ic_launcher.png'), 48)
print("icons created")
