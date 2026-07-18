# 记忆笔记 (NoteSketch)

一个免登录的安卓学习笔记 App：记录笔记，并按 **艾宾浩斯记忆曲线** 在详情时间线上勾选完成复习。

## 功能
- 打开即用，无需注册 / 登录
- 新建学习笔记（标题 + 内容）
- 保存后按艾宾浩斯曲线安排 6 次复习节点：**第 1、2、4、7、15、30 天**
- 点击列表笔记查看详情与复习时间线
- 按顺序勾选时间线节点完成复习；全部完成后标记「已完成全部复习」
- 支持删除笔记

## 技术栈
- Kotlin + ViewBinding
- Room 本地数据库（数据全部存本机，不联网）
- 最低支持 Android 7.0 (API 24)，编译 SDK 34

## 目录结构
```
app/src/main/java/com/example/notesketch/
├── MainActivity.kt          # 笔记列表
├── AddNoteActivity.kt       # 新建笔记
├── NoteDetailActivity.kt    # 详情与复习时间线
├── NoteAdapter.kt           # 列表适配器
├── TimelineAdapter.kt       # 时间线勾选适配器
├── Ebbinghaus.kt            # 艾宾浩斯间隔与阶段计算
├── DateUtil.kt              # 时间友好文案
└── data/                    # Note / NoteDao / AppDatabase
```

## 如何运行
1. 用 **Android Studio** 打开本项目根目录。
2. 首次打开时 Android Studio 会自动补全 Gradle Wrapper（`gradlew` 及 `gradle-wrapper.jar`）并同步依赖。
3. 连接手机或启动模拟器，点击 Run 即可安装运行。

> 命令行构建：`./gradlew assembleDebug`（需已生成 gradle-wrapper.jar）。
