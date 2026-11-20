package app.yoniso.core

/** Why bells won't ring as set up, shown on the bells screen. */
enum class BellProblem {
    /** No active day is chosen, so nothing ever rings. */
    NO_DAYS,

    /** Do Not Disturb is on now: bells are skipped. */
    DO_NOT_DISTURB,

    /** The phone is on silent now: bells are skipped. */
    SILENT,

    /**
     * Neither exact alarms nor the accessibility service: Android won't let the bell play from the
     * background, so bells only vibrate.
     */
    VIBRATE_ONLY,

    /** No exact alarms: Android may deliver bells several minutes late. */
    MAY_BE_LATE,
}

object BellProblems {
    /** The problems with [settings] given the phone's state, most important first. None while bells are off. */
    fun of(
        settings: BellSettings,
        exactAlarmsAllowed: Boolean,
        accessibilityOn: Boolean,
        doNotDisturb: Boolean,
        silent: Boolean,
    ): List<BellProblem> {
        if (!settings.enabled) return emptyList()
        return buildList {
            if (settings.activeHours.days.isEmpty()) add(BellProblem.NO_DAYS)
            if (doNotDisturb) add(BellProblem.DO_NOT_DISTURB) else if (silent) add(BellProblem.SILENT)
            if (!exactAlarmsAllowed) {
                // Vibration plays from the alarm's receiver, so it needs neither.
                add(if (settings.output.sound && !accessibilityOn) BellProblem.VIBRATE_ONLY else BellProblem.MAY_BE_LATE)
            }
        }
    }
}
