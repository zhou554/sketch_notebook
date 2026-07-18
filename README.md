# 记忆笔记 (NoteSketch)

一个免登录的安卓学习笔记 App：记录笔记，并按 **艾宾浩斯记忆曲线** 自动提醒复习。

## 功能
- 打开即用，无需注册 / 登录
- 新建学习笔记（标题 + 内容）
- 保存后自动按艾宾浩斯曲线安排 6 次复习：**第 1、2、4、7、15、30 天**
- 到点通过系统通知提醒「该复习啦」
- 列表中点「已复习」推进到下一阶段；全部完成后标记「已完成全部复习」
- 支持删除笔记；设备重启后自动恢复复习提醒

## 技术栈
- Kotlin + ViewBinding
- Room 本地数据库（数据全部存本机，不联网）
- AlarmManager + 通知栏做定时复习提醒
- 最低支持 Android 7.0 (API 24)，编译 SDK 34

## 目录结构
```
app/src/main/java/com/example/notesketch/
├── MainActivity.kt        # 笔记列表 / 复习操作
├── AddNoteActivity.kt     # 新建笔记
├── NoteAdapter.kt         # 列表适配器
├── Ebbinghaus.kt          # 艾宾浩斯间隔与阶段计算
├── ReviewScheduler.kt     # 安排/取消复习闹钟
├── ReminderReceiver.kt    # 到点弹出复习通知
├── BootReceiver.kt        # 重启后恢复闹钟
├── DateUtil.kt            # 复习时间友好文案
└── data/                  # Note / NoteDao / AppDatabase
```

## 如何运行
1. 用 **Android Studio** 打开本项目根目录。
2. 首次打开时 Android Studio 会自动补全 Gradle Wrapper（`gradlew` 及 `gradle-wrapper.jar`）并同步依赖。
3. 连接手机或启动模拟器，点击 Run 即可安装运行。

> 命令行构建：`./gradlew assembleDebug`（需已生成 gradle-wrapper.jar）。

## 说明
- 首次使用会申请「通知权限」（Android 13+），请允许，否则收不到复习提醒。
- 部分厂商系统对后台精确闹钟有限制，建议在系统设置中允许本应用「自启动 / 精确闹钟」以保证提醒准时。
