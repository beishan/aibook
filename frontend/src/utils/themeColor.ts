const HEX_PATTERN = /^#[0-9a-f]{6}$/i
type Rgb = { r: number; g: number; b: number }

export const normalizeHexColor = (value: unknown): string | null => {
  if (typeof value !== 'string') return null
  const normalized = value.trim().toUpperCase()
  return HEX_PATTERN.test(normalized) ? normalized : null
}

const toRgb = (hex: string): Rgb => ({
  r: Number.parseInt(hex.slice(1, 3), 16),
  g: Number.parseInt(hex.slice(3, 5), 16),
  b: Number.parseInt(hex.slice(5, 7), 16),
})

const toHex = (rgb: Rgb) => `#${[rgb.r, rgb.g, rgb.b]
  .map(value => Math.round(value).toString(16).padStart(2, '0')).join('')}`.toUpperCase()

const mix = (hex: string, target: Rgb, weight: number) => {
  const source = toRgb(hex)
  return toHex({
    r: source.r + (target.r - source.r) * weight,
    g: source.g + (target.g - source.g) * weight,
    b: source.b + (target.b - source.b) * weight,
  })
}

const alpha = (hex: string, opacity: number) => {
  const { r, g, b } = toRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${opacity})`
}

export const createThemeColorTokens = (value: string) => {
  const color = normalizeHexColor(value) || '#2E7D5A'
  const light = mix(color, { r: 255, g: 255, b: 255 }, 0.24)
  const dark = mix(color, { r: 0, g: 0, b: 0 }, 0.2)
  return {
    '--primary': color,
    '--primary-light': light,
    '--primary-dark': dark,
    '--primary-gradient': `linear-gradient(135deg, ${dark}, ${light})`,
    '--primary-alpha-10': alpha(color, 0.1),
    '--primary-alpha-15': alpha(color, 0.15),
    '--primary-alpha-20': alpha(color, 0.2),
    '--primary-alpha-30': alpha(color, 0.3),
    '--el-color-primary': color,
    '--el-color-primary-dark-2': dark,
    '--el-color-primary-light-3': mix(color, { r: 255, g: 255, b: 255 }, 0.3),
    '--el-color-primary-light-5': mix(color, { r: 255, g: 255, b: 255 }, 0.5),
    '--el-color-primary-light-7': mix(color, { r: 255, g: 255, b: 255 }, 0.7),
    '--el-color-primary-light-8': mix(color, { r: 255, g: 255, b: 255 }, 0.8),
    '--el-color-primary-light-9': mix(color, { r: 255, g: 255, b: 255 }, 0.9),
  } as Record<string, string>
}

export const applyThemeColorTokens = (color: string) => {
  Object.entries(createThemeColorTokens(color)).forEach(([name, value]) => {
    document.documentElement.style.setProperty(name, value)
  })
}
