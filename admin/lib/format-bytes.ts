const UNITS = ["B", "KB", "MB", "GB", "TB"];
const STEP = 1024;

export function formatBytes(bytes: number, locale: string): string {
    let value = bytes;
    let unitIndex = 0;

    while (value >= STEP && unitIndex < UNITS.length - 1) {
        value /= STEP;
        unitIndex++;
    }

    const fractionDigits = unitIndex === 0 || value >= 100 ? 0 : 1;
    const formatted = value.toLocaleString(locale, {
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
    });

    return `${formatted} ${UNITS[unitIndex]}`;
}
