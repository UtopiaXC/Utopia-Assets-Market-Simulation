/**
 * Utility functions for formatting numbers, axis labels, and file sizes.
 */

/**
 * Compact formatting for ECharts axis numbers (e.g. 10K, 2.5M, 1.2B).
 * Prevents multi-digit numbers from overflowing or clipping on the left margin.
 */
export function formatAxisNumber(val: number | string): string {
  const num = Number(val)
  if (isNaN(num)) return String(val)
  const abs = Math.abs(num)
  if (abs >= 1e12) return (num / 1e12).toFixed(1) + 'T'
  if (abs >= 1e9) return (num / 1e9).toFixed(1) + 'B'
  if (abs >= 1e6) return (num / 1e6).toFixed(1) + 'M'
  if (abs >= 1e3) return (num / 1e3).toFixed(1) + 'K'
  if (abs > 0 && abs < 0.01) return num.toExponential(1)
  return num % 1 === 0 ? num.toFixed(0) : num.toFixed(2)
}

/**
 * Human-readable byte size formatting (e.g. 120 KB, 14.5 MB, 1.2 GB).
 */
export function formatFileSize(bytes: number): string {
  if (bytes === null || bytes === undefined || isNaN(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const idx = Math.min(Math.max(0, i), units.length - 1)
  return (bytes / Math.pow(1024, idx)).toFixed(idx === 0 ? 0 : 1) + ' ' + units[idx]
}
