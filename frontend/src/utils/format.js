/** 把 star 数量格式化成紧凑形式：>=1000 显示为 k，如 1500 -> 1.5k */
export function formatStars(n) {
  return n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)
}
