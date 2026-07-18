package com.example.notesketch

/**
 * 艾宾浩斯记忆曲线复习间隔（相对创建时间的天数）。
 * 复习节点：第 1、2、4、7、15、30 天。
 */
object Ebbinghaus {

    /** 各阶段距离“创建时间”的间隔天数 */
    val INTERVAL_DAYS = longArrayOf(1, 2, 4, 7, 15, 30)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 阶段总数 */
    val stageCount: Int get() = INTERVAL_DAYS.size

    /**
     * 根据创建时间和阶段，计算该阶段的复习时间戳。
     * 超过最后阶段返回 -1，表示复习流程结束。
     */
    fun reviewTimeFor(createdAt: Long, stage: Int): Long {
        if (stage >= INTERVAL_DAYS.size) return -1
        return createdAt + INTERVAL_DAYS[stage] * DAY_MS
    }

    /** 人类可读的阶段描述，例如 "第 3 次复习 (第 4 天)" */
    fun stageLabel(stage: Int): String {
        if (stage >= INTERVAL_DAYS.size) return "已完成全部复习"
        return "第 ${stage + 1} 次复习 (第 ${INTERVAL_DAYS[stage]} 天)"
    }
}
