/**
 * NoteSketch 预览数据模型
 * 与 Android 源码保持同步：
 * - colors.xml / themes.xml
 * - Ebbinghaus.kt / DateUtil.kt
 * - 各 layout XML 文案
 *
 * 改 App 功能或 UI 时，请同步更新本文件与 index.html / app.css / app.js
 */
window.NoteSketchModel = {
  meta: {
    appName: "记忆笔记",
    packageId: "com.example.notesketch",
    syncedFromBranch: "test_UI",
    syncedCommitHint: "优化界面UI设计 + sequential review timeline",
    updatedAt: "2026-07-25",
  },

  colors: {
    bg: "#FFFFFF",
    ink: "#1C1C1C",
    textPrimary: "#1C1C1C",
    textSecondary: "#8A8A8A",
    line: "#E6E6E6",
    due: "#B33A3A",
    white: "#FFFFFF",
  },

  /** 对应 Ebbinghaus.INTERVAL_DAYS */
  intervalDays: [1, 2, 4, 7, 15, 30],

  strings: {
    mainHeader: "记忆笔记",
    empty:
      "还没有笔记\n点击右下角 + 记录第一条学习笔记",
    addHeader: "新建笔记",
    titleHint: "标题（如：英语单词 Unit 3）",
    contentHint: "笔记内容 / 知识点...",
    save: "保存",
    detailHeader: "复习详情",
    timelineTitle: "艾宾浩斯复习时间线",
    timelineHelper: "按顺序勾选完成每次复习，不可跳过或取消",
    noBody: "（无正文）",
    delete: "删除",
    toastNeedTitle: "请填写标题",
    toastSaved: "已保存",
    finishedAll: "已完成全部复习",
    due: "待复习",
  },

  stageLabel(stage) {
    const days = this.intervalDays;
    if (stage >= days.length) return this.strings.finishedAll;
    return `第 ${stage + 1} 次复习 (第 ${days[stage]} 天)`;
  },

  reviewTimeFor(createdAt, stage) {
    const days = this.intervalDays;
    if (stage >= days.length) return -1;
    return createdAt + days[stage] * 24 * 60 * 60 * 1000;
  },

  formatDateTime(timeMs) {
    if (timeMs <= 0) return "—";
    const d = new Date(timeMs);
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    const hh = String(d.getHours()).padStart(2, "0");
    const mi = String(d.getMinutes()).padStart(2, "0");
    return `${mm}月${dd}日 ${hh}:${mi}`;
  },

  reviewText(nextReviewTime, finished) {
    if (finished) return this.strings.finishedAll;
    const now = Date.now();
    const diff = nextReviewTime - now;
    const dayMs = 24 * 60 * 60 * 1000;
    if (diff <= 0) return this.strings.due;
    if (diff < dayMs) return `下次复习：今天 ${this.formatDateTime(nextReviewTime)}`;
    if (diff < 2 * dayMs) return "下次复习：明天";
    return `下次复习：${this.formatDateTime(nextReviewTime)}`;
  },

  isDue(nextReviewTime, finished) {
    return !finished && nextReviewTime <= Date.now();
  },
};
