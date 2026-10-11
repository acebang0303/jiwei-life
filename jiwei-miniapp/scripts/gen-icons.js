// 生成 tabBar 图标 PNG（81x81，两色：灰 #9C8A7C / 橙 #FF7A3D）
// 用法: node scripts/gen-icons.js
const zlib = require('zlib')
const fs = require('fs')
const path = require('path')

const SIZE = 81
const GRAY = [0x9c, 0x8a, 0x7c]
const ORANGE = [0xff, 0x7a, 0x3d]

// ---- 图形形状判定（坐标归一化到 0-1） ----
const shapes = {
  home: (x, y) => {
    // 屋顶三角形 + 屋身矩形 + 门
    const roof = y >= 0.18 && y <= 0.42 && Math.abs(x - 0.5) <= (0.42 - y) * 1.35
    const body = y >= 0.38 && y <= 0.82 && x >= 0.2 && x <= 0.8
    const door = y >= 0.55 && y <= 0.82 && x >= 0.42 && x <= 0.58
    return (roof || body) && !door
  },
  note: (x, y) => {
    // 纸张 + 三行文字线
    const paper = y >= 0.16 && y <= 0.84 && x >= 0.24 && x <= 0.76
    const line1 = y >= 0.3 && y <= 0.34 && x >= 0.32 && x <= 0.68
    const line2 = y >= 0.42 && y <= 0.46 && x >= 0.32 && x <= 0.68
    const line3 = y >= 0.54 && y <= 0.58 && x >= 0.32 && x <= 0.56
    return paper && !line1 && !line2 && !line3
  },
  cart: (x, y) => {
    // 把手 + 车筐 + 轮子
    const handle = y >= 0.2 && y <= 0.28 && ((x >= 0.22 && x <= 0.32) || (x >= 0.52 && x <= 0.62))
    const handleBar = y >= 0.24 && y <= 0.3 && x >= 0.28 && x <= 0.56
    const basket = y >= 0.3 && y <= 0.58 && x >= 0.18 && x <= 0.68
    const basketEdge = y >= 0.3 && y <= 0.34 && x >= 0.14 && x <= 0.72
    const wheel1 = Math.hypot(x - 0.3, y - 0.68) <= 0.1
    const wheel2 = Math.hypot(x - 0.56, y - 0.68) <= 0.1
    return (handle || handleBar || basketEdge || basket) && !wheel1 && !wheel2
  },
  profile: (x, y) => {
    // 头 + 肩
    const head = Math.hypot(x - 0.5, y - 0.32) <= 0.16
    const body = y >= 0.55 && y <= 0.82 && Math.abs(x - 0.5) <= (0.82 - y) * 1.1
    return head || body
  }
}

function draw(shapeName, color) {
  const raw = Buffer.alloc(SIZE * SIZE * 4)
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const inside = shapes[shapeName]((x + 0.5) / SIZE, (y + 0.5) / SIZE)
      const idx = (y * SIZE + x) * 4
      if (inside) {
        raw[idx] = color[0]
        raw[idx + 1] = color[1]
        raw[idx + 2] = color[2]
        raw[idx + 3] = 255
      } else {
        raw[idx] = 0
        raw[idx + 1] = 0
        raw[idx + 2] = 0
        raw[idx + 3] = 0
      }
    }
  }
  return encodePng(raw)
}

function encodePng(rawRgba) {
  const IHDR = Buffer.alloc(13)
  IHDR.writeUInt32BE(SIZE, 0)
  IHDR.writeUInt32BE(SIZE, 4)
  IHDR[8] = 8 // bit depth
  IHDR[9] = 6 // color type RGBA
  // 每行前加 filter byte 0
  const stride = SIZE * 4
  const data = Buffer.alloc((stride + 1) * SIZE)
  for (let y = 0; y < SIZE; y++) {
    data[y * (stride + 1)] = 0
    rawRgba.copy(data, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
  }
  const idat = zlib.deflateSync(data)

  function chunk(type, buf) {
    const len = Buffer.alloc(4)
    len.writeUInt32BE(buf.length)
    const typeBuf = Buffer.from(type, 'ascii')
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, buf])) >>> 0)
    return Buffer.concat([len, typeBuf, buf, crc])
  }

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', IHDR),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0))
  ])
}

// CRC32 表
const crcTable = (() => {
  const t = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    }
    t[n] = c >>> 0
  }
  return t
})()
function crc32(buf) {
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) {
    c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  }
  return (c ^ 0xffffffff) >>> 0
}

const outDir = path.join(__dirname, '..', 'src', 'static', 'icons')
fs.mkdirSync(outDir, { recursive: true })

const icons = [
  ['home', 'home'], ['home', 'home-active'],
  ['note', 'note'], ['note', 'note-active'],
  ['cart', 'cart'], ['cart', 'cart-active'],
  ['profile', 'profile'], ['profile', 'profile-active']
]

for (const [shape, name] of icons) {
  const color = name.endsWith('-active') ? ORANGE : GRAY
  const png = draw(shape, color)
  fs.writeFileSync(path.join(outDir, name + '.png'), png)
  console.log('生成', name + '.png', png.length, 'bytes')
}
