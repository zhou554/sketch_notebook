# 网页预览 (preview)

可交互的 **记忆笔记** UI / 功能预览，视觉与行为对齐当前 Android App（`no_ring` / `test_UI` 线）。

## 打开方式

- 直接用浏览器打开 `preview/index.html`
- 或在 `preview` 目录启动本地服务：

```bash
npx --yes serve .
```

## 目录

| 文件 | 作用 |
|------|------|
| `index.html` | 预览台 + 手机框三屏结构 |
| `app.css` | 对齐 App 的配色与布局 |
| `app-model.js` | 颜色、文案、艾宾浩斯间隔（与 Kotlin 同步的「单一数据源」） |
| `app.js` | 列表 / 新建 / 详情 / 时间线交互 |

预览数据存在浏览器 `localStorage`，刷新不会丢；清空站点数据可重置示例笔记。

## 与 App 同步

改 App 后请同步改本目录。项目规则见 `.cursor/rules/preview-sync.mdc`。
