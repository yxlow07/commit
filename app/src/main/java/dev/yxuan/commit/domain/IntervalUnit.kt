package dev.yxuan.commit.domain

/**
 * Units for "repeat every N … after I finish it" (F4.2). The interval is stored as days
 * (`recur_interval_days`, shared with the web app and sync), so a week is 7 days, a month 30 and a
 * year 365: exact for weeks, approximate for months and years, which is fine for "after I finish it".
 */
enum class IntervalUnit(val days: Int, val singular: String, val plural: String) {
    DAY(1, "day", "days"),
    WEEK(7, "week", "weeks"),
    MONTH(30, "month", "months"),
    YEAR(365, "year", "years");

    fun word(n: Int) = if (n == 1) singular else plural

    companion object {
        /** The largest unit that divides [days] exactly: 14 → 2 weeks, 60 → 2 months, 5 → 5 days. */
        fun split(days: Int): Pair<Int, IntervalUnit> {
            val d = days.coerceAtLeast(1)
            val unit = entries.reversed().first { d % it.days == 0 }
            return d / unit.days to unit
        }

        /** "day", "2 weeks", "month", "3 years". */
        fun describe(days: Int): String {
            val (n, unit) = split(days)
            return if (n == 1) unit.singular else "$n ${unit.plural}"
        }
    }
}
