# 应用管家（AppManager）

给车机用的轻量应用管理工具。23 款领克 03（LYNK OSN / Android 9）的设置被阉割，
没有「安装未知应用」等权限入口，这个 App 提供直达电梯，一键打开系统里藏着的权限页。

## 功能

- 应用列表：默认显示用户安装的应用，Tab 分类（第三方 / 系统）+ 按名称/包名搜索，
  每行显示应用名、包名、版本号（含 versionCode），自适应多列宫格
  （用 getInstalledApplications / getInstalledPackages / launcher 查询三路合并，
  顶部显示总数；若车机限制枚举，可点「诊断」看各路返回数）
- 点某个应用，直达：
  - 允许安装未知应用
  - 应用信息
  - 悬浮窗权限
  - 通知管理
  - 忽略电池优化
- 管理操作：打开应用、卸载（先弹二次确认框，确认后直接调系统卸载，不经过应用详情页）、
  清除数据、强行停止
  （清除数据/强行停止 Android 没有公开直达 intent，会跳到应用信息页对应位置）
- 顶部快捷入口：未知来源应用总表、全部应用、系统设置首页

如果某个页面打不开（被车机屏蔽），会弹 Toast 提示，不会闪退。

## 技术

- Kotlin，`minSdk 28 / targetSdk 28`，对齐车机 Android 9
- 零危险权限（只声明了 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，普通权限）
- 跳转全部走标准 `Settings` Intent，失败时捕获并提示

## 构建

```bash
gradle :app:assembleDebug
```

GitHub Actions 会在 push 到 `main` 后自动打包，产物按
`appmanager-<version>-<yyMMddHHmm>.apk`（北京时间）命名上传。

## 安装说明

debug 包用 CI runner 临时生成的签名，每次构建签名都不同，
覆盖安装前请先卸载旧版本（全新安装不受影响）。

## License

MIT
