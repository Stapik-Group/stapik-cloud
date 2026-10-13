import type { components } from "@/lib/api-types";

type DailyWrites = components["schemas"]["DashboardDailyWrites"];

const CHART_HEIGHT = 120;
const BAR_WIDTH = 10;
const BAR_GAP = 4;
const MIN_VISIBLE_BAR_HEIGHT = 2;

export function WritesChart({ days, locale }: { days: DailyWrites[]; locale: string }) {
    const maxCount = Math.max(...days.map((day) => day.count), 1);
    const width = days.length * (BAR_WIDTH + BAR_GAP) - BAR_GAP;

    return (
        <svg
            viewBox={`0 0 ${width} ${CHART_HEIGHT}`}
            className="w-full h-32 text-primary"
            preserveAspectRatio="none"
            role="img"
        >
            {days.map((day, index) => {
                const barHeight =
                    day.count === 0
                        ? MIN_VISIBLE_BAR_HEIGHT
                        : Math.max((day.count / maxCount) * CHART_HEIGHT, MIN_VISIBLE_BAR_HEIGHT);

                return (
                    <rect
                        key={day.date}
                        x={index * (BAR_WIDTH + BAR_GAP)}
                        y={CHART_HEIGHT - barHeight}
                        width={BAR_WIDTH}
                        height={barHeight}
                        rx={2}
                        fill="currentColor"
                        opacity={day.count === 0 ? 0.2 : 1}
                    >
                        <title>{`${new Date(day.date).toLocaleDateString(locale, { timeZone: "UTC" })}: ${day.count}`}</title>
                    </rect>
                );
            })}
        </svg>
    );
}
