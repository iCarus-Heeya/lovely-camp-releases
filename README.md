# 老婆的小书架

一个私人安卓小说阅读 App，专门为老婆使用而设计。

## 当前状态

- 面向个人长期使用的 Android 小说阅读 App，已经具备正式 release APK。
- 温柔奶油风 Compose UI。
- 书架、搜索、详情、阅读器、设置/小纸条页面。
- 阅读器支持滚动进度回写、字号调节、晚安灯、底部固定阅读工具条和当前目录提示。
- 排行榜和随便看看支持查看详情、直接加入书架。
- 明显但克制的专属文案：
  - 老婆的小书架
  - 帮老婆找书
  - 老公的小纸条
  - 晚安灯
  - 想你的这一页
- 来源插件接口。
- 当前只预置用户提供的 6 个来源。
- 已验证闭环来源：
  - `ixdzs8.com`：搜索、详情、章节列表、章节内容解析；代码强制同源、HTTPS、规范化路径后禁止 `/down` 与 `/download/`。
  - `m.qisuwang.cc`：通过可访问镜像解析分类/排行、详情、TXT 下载并导入阅读。
  - `www.qinkan.net`：解析列表/详情，多格式下载，TXT 可导入阅读。
  - `zxcs.zip`：解析列表/排行、详情、TXT 下载并导入阅读。
- 预置但未完整支持来源：
  - `m.ijjxs.com`：搜索解析器存在；当前网络访问 `m/www + http/https` 均超时，详情/下载暂不声明支持。
  - `yqxz.cc`：Cloudflare 拦截，预置但不计入已支持；不绕过。
- 本地内存书库：
  - 加入书架
  - 阅读进度
  - 书签
  - 老公小纸条
- Android SharedPreferences 持久化：
  - 书架快照
  - 阅读进度
  - 书签
  - 小纸条
- 单元测试源码：
  - 来源解析测试
  - 本地书库测试
  - 持久化编码测试
  - 搜索结果合并测试
  - 阅读资格校验测试
  - 重定向安全策略测试

## 合规边界

- 只用于个人本地阅读。
- 不绕过登录、付费、VIP、反爬、地域限制或加密。
- 当前按来源已知规则阻断已识别的禁止下载路径；完整动态 robots.txt 校验会在启用下载/缓存前补齐。
- 单来源请求有间隔，搜索按钮会在请求中禁用并取消上一轮搜索。
- 下载/导入能力只在已验证来源中启用；未验证允许的离线能力不在适配器里声明。
- 搜索结果、章节链接和 HTTP 重定向都会经过同源/禁用路径校验，避免跨站抓取。

## 本机验证状态

已在本机补齐便携式工具链：

- OpenJDK 17：`D:\AgentWorkspace\.toolchains\jdk17\jdk-17.0.19+10`
- Gradle 8.10.2：`D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2`
- Android SDK：`D:\AgentWorkspace\.toolchains\android-sdk`

已执行并通过：

```powershell
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat testDebugUnitTest --stacktrace
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat assembleRelease --stacktrace
```

当前测试覆盖包含：

- `LibraryRepositoryTest`
- `LibrarySnapshotCodecTest`
- `IjjxsSourceTest`
- `IxdzsSourceTest`
- `QinkanSourceTest`
- `QisuwangSourceTest`
- `ZxcsSourceTest`
- `AggregatedNovelCatalogTest`
- `RedirectPolicyTest`
- `SearchResultMergerTest`
- `SourceReadEligibilityTest`
- `SourceSafetyTest`

产物：

- 正式个人使用 APK：`app/build/outputs/apk/release/app-release.apk`
- 本地 release 签名文件：`lovely-reader-release.jks`
- 签名配置：`release-signing.properties`

重要：后续覆盖升级必须继续使用同一个 `lovely-reader-release.jks`。请保留这个文件和 `release-signing.properties`。

补充自查：

- 未发现 TODO/FIXME/未实现异常标记；仍保留少量本地示例数据用于空书架体验和网络失败兜底。
- 未发现未验证离线能力声明。
- 来源同源/禁止路径测试通过。
- 来源禁止路径绕过测试通过。
- 章节外站链接过滤测试通过。
- HTTP 重定向安全复核测试通过。
- 搜索结果合并不会把可阅读能力绑定到错误来源。
- 阅读资格校验复用来源安全规则，不会把 open-original-only 来源或禁用路径带入站内阅读器。
- 书库 snapshot/restore 测试通过。
- 书库持久化编码/解码测试通过。

## 构建条件

项目已生成 Gradle wrapper。网络可访问 Gradle 分发包时，优先运行：

```powershell
$env:JAVA_HOME='D:\AgentWorkspace\.toolchains\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME='D:\AgentWorkspace\.toolchains\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
.\gradlew.bat test
.\gradlew.bat assembleRelease
```

如果 wrapper 首次下载 Gradle 分发包超时，可使用当前便携式工具链运行：

```powershell
$env:JAVA_HOME='D:\AgentWorkspace\.toolchains\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME='D:\AgentWorkspace\.toolchains\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat test
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat assembleRelease
```


