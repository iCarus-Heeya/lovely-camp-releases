# Project Memory

## 2026-10-02 — v0.9.23+110 排行榜全量范围与制作国家修正（当前本地候选）

- 排行榜目录已从“票数≥5000/23k 条”扩展为 IMDb 公共评分 `numVotes>=1000` 的 63,681 条，`catalogVersion=2026-10-02.3`、`methodologyVersion=imdb-rated-scope-v2-tvmaze-enriched`；CN 2024/2025/2026 分别 33/37/9。
- `countries` 优先使用 Wikidata P495、TVmaze network country 或审核标题桥接；IMDb AKA `region` 只保存为 `releaseMarkets`，避免把发行市场当制作国。模糊 Wikipedia 标题搜索不再覆盖可信中文名。
- 源码版本已递增为 `versionName=0.9.23`、`versionCode=110`；尚未完成本候选的 Android/MuMu/签名证据或 public release，不得把旧 v0.9.22 证据冒充新版本。

## 2026-09-30 — 追剧多源排行榜内置快照与 v0.9.20+107 本地候选

- 生产排行榜不再返回 `SERVICE_UNAVAILABLE`：`BundledRankingRepository` 读取 `app/src/main/assets/ranking/catalog.json`，由 `ranking-service/build_catalog.py` 生成 IMDb 公共数据集 + TVmaze 公共 API 快照；92 条（电影 28、剧集 32、综艺 32），73 张受控包内海报。
- `RankingContract` 支持受限 `asset://ranking/posters/...`，`RankingPoster` 使用 `ContentScale.Fit`，卡片显示来源评分/榜位、综合参考分、覆盖度、摘要和去搜索；刷新状态禁用重复点击并显示进度。
- `0.9.20/107` Debug SHA `C8CBD382484C680D1A0A6F0FC83F9B7533A1032B8B656A89C5808AEE24FA2496`，Release SHA `F1ACE67F798000CEFC65D17DBC0CC5188518C219FC17E9AFCB14D0535FE9517D`；根目录只保留这对版本化 APK，旧 `0.9.19` pair 已归档。
- Gradle `testDebugUnitTest assembleDebug assembleRelease`：568 tests、0 failures、0 errors、8 skipped；Release v2 单签名、zipalign 通过。MuMu `127.0.0.1:16384` 实测榜单/筛选/海报/搜索桥接/返回/刷新/有声隔离，crash buffer 为空；证据见 `artifacts/candidates/20260930-ranking-v0.9.20/`。
- 2026-10-01 增量：刷新保留旧快照至少 220ms 并显示 spinner/banner，长筛选行增加“左右滑动查看更多”；`RankingPoster` 对远程海报只占位、不发起未地址绑定请求。中文工作区直接跑 Gradle 的统一 `ClassNotFoundException` 已确认是路径编码环境问题，使用临时 ASCII `subst R:` 重跑后 568/0/0/8 成功，记录见 `docs/ranking/bug-experience.md`。
- 仍不是 public release：没有托管实时 feed、TMDB/Trakt 实际接入、服务端授权/归因、排行榜概念图 golden baseline、GitHub Latest/tag/manifest 验证；最终答复必须区分 `source + local candidate` 与 `public release`。

## 2026-10-01 — PO 收口与 Release 实机补充

- SA、UX、QA、QC-数据、QC-回归、QC-视觉均已完成独立终审并写入 `docs/ranking/evidence/2026-10-01-Bundled-*.md`；PO 汇总见 `2026-10-01-PO-closure.md`。条件项保持原样，不把短刷新窗口或缺少 golden 写成无条件通过。
- MuMu `127.0.0.1:16384` 另行安装并启动 Release `0.9.20+107`，榜单首屏/入口/内置海报实际渲染，进程存活且 crash buffer 为空；新增证据在候选 `27-release-launch.png` 至 `31-release-refresh-loop.png`。
- 候选 `SHA256SUMS.txt` 已重算并校验 `63 entries / 0 bad`；根目录仍只有当前版本化 Debug/Release APK 对。仍未创建公开 tag/Latest Release/manifest，也没有排行榜专用 golden baseline 或实时聚合 feed。

## 2026-09-01 — v0.9.3+90 正常版本递增构建（未正式发布）

- 按用户要求，Android 从 `0.9.2/89` 正常递增到 `0.9.3/90`；源码位于 `app/build.gradle.kts`。Debug/Release 均重新构建，包内 `aapt` 元数据、签名和 zipalign 已核验。
- 当前根目录交付对为 `老婆的小营地-debug-v0.9.3.apk`（49,376,881 bytes，SHA-256 `2734C9C762411A539FF8B40C9D931D7D50691EC0F74CE38C2C25628F76FCEDC7`）和 `老婆的小营地-v0.9.3.apk`（38,014,457 bytes，SHA-256 `D8685664DC535CB97B9B3404B4D98D684321A7122D56252057E39807108D4D2A`）；源输出、候选和根目录副本一致。
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 成功；JUnit XML 汇总 `488 tests / 0 failures / 0 errors / 8 skipped`。旧 `0.9.2` 根包已归档到 `artifacts/releases/legacy/20260901-v0.9.2-before-0.9.3/`。
- 本轮是版本递增和重新打包，没有重新安装 MuMu；前一轮 r5 的全链路证据仍绑定 `0.9.2/89`，不能冒充 `0.9.3/90` 的新设备签收。当前仍未执行正式公开 Release。

## 2026-09-01 — Round28 用户审查整改与 r5 代表章节闭环（未正式发布）

- Sites 公开调试站已部署 v25：源码提交 `c55ae690af3816ef3e440ca9d379eb84c8a8c5f6`，deployment `appgdep_6a966e6dc9a08191865386e197bd95cb`，环境 revision 18。服务端新增稳定匿名 client identity：浏览器使用 HttpOnly Cookie，Android 使用内部 `X-Reader-Client-Id`；用户不输入令牌、地址、代理或模型。
- Android r5 Debug 候选（历史现场包）位于 `artifacts/candidates/session-identity-import-20260901-r5/老婆的小营地-debug-v0.9.2-89.apk`，49,376,887 bytes，SHA-256 `0AB3736798F8A59A02D709832C13E5E888936636C16D0D68D6281EB7F1C253B9`；MuMu `127.0.0.1:16416` installed `base.apk` SHA 一致。当前版本化交付物为上方 `0.9.3/90`。
- Android 当前全量单测为 488 tests、0 failures、0 errors、8 skipped；lint 和 assembleDebug 成功。新增导入意图幂等键：正文 SHA + 标题/作者 + 生成范围 + pipeline 契约；同一正文改名不再撞旧幂等键并被误报服务不可用，409 有用户文案。
- MuMu r5 干净首屏已证明自动连接、无用户 token/address/proxy/model 字段；当前人类命名 fixture 《雨停之前-r2》源 SHA `33a4df1e8a2acd8a8de8db7f6a8306288d3a9b6e3ed7f864010bbdadef6379d6`、目标 book ID `bb0a1eda-aa5e-459a-b18e-cc378f50363c`。worker 使用 `AUDIOBOOK_TARGET_BOOK_ID`，避免旧队列任务污染；本地 Qwen3-4B 已完成整本分析，2/2 代表章节已生成、下载并完成前台/后台播放验证。
- 复核流程教训：角色报告必须绑定当前候选 SHA、installed SHA、目标 book ID、输入 SHA 和干净设备；“worker 有日志/CPU”“源码看起来正确”“条件通过”均不能替代用户黑盒路径。Round27 发送的共享事实中曾出现一处候选 SHA 手工转录错误并在报告前纠正，后续必须由机器生成身份清单，禁止手填哈希。已新增 `docs/tts/user-review-protocol.md` 与 `scripts/audiobook_user_acceptance_gate.py`；缺少 UI hierarchy 证据统一为 `UNVERIFIED`。
- 当前统一追溯清单为 `docs/tts/evidence/audiobook-e2e-20260901/round28-binding-manifest.json`，把 r5 APK、已安装包、Site v25 deployment、输入源、book ID、Qwen 分析 manifest、两章 worker/手机音频及 MediaSession 原始证据绑定在一起；`mumu-cleanup-r5.txt` 记录了精确测试 VM、代理和端口的清理结果。之前 QC 的 85/85 初始化失败来自未带正确 Gradle/JDK/离线参数的命令，最新原始日志已按固定命令复现 488/0/0/8；此前 Ops 把其他程序的 7897/7078 监听误认作测试残留，已根据进程归属纠正为非项目程序。
- 交付边界：当前仍是公开 Site + 本地 Debug 候选，不是正式 Android Release；未生成签名 Release/tag/公开 APK asset/manifest。测试文本为自造 fixture，不是《三生三世十里桃花》授权正文；人工老听客听感尚未签收。

## 2026-08-29 — 公开 Sites 与 MuMu 听书全链路验收（非 APK 发布）

- Sites 试验站已改为 public：`https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`；页面公开但 API 继续使用客户端/worker bearer token。最新 Sites version 4 / deployment `appgdep_6a91e32155608191a143e550f5e5be2f`，源码提交 `544afaabb371e1891e8e0bed7fd34c62d649bcca`。
- MuMu `127.0.0.1:16416` 通过主机代理完成第二本测试书上传、状态刷新、2 章音频下载、前台播放、切后台播放和重新进入恢复；书籍 `1d0d87de-876e-4082-9216-a57c2f492e16` 达到 `ready 2/2 100%`。本地 WAV 哈希与服务端一致，MediaSession 后台 position 持续增长，服务 `isForeground=true`。
- Android 书架已改为整体纵向滚动；MediaSessionService Manifest 声明、固定请求体长度、Debug 跳过自动更新器均已验证。最新 Debug APK 候选位于 `artifacts/candidates/audiobook-e2e-20260829/老婆的小营地-debug-v0.9.1.apk`，未复制覆盖项目根目录，未发布。
- 回归：worker 77 tests 全通过；Sites 8 tests/build 通过；Android 402 tests 无失败/错误、8 skipped，Debug/Release build 通过。最新候选 Debug SHA-256 为 `AB576CC3A020BAA04A4626CB9B9EF20CC5C4D76150389CAE05D09E013B7FF324`，Release SHA-256 为 `2F0D04640C9A75242A54874E1ECF097CDFB54686AB1AD0B499B5B24CDC6F04BF`。
- 网络边界：MuMu 直连公开 Sites 被 Cloudflare 403，代理链路通过；公司电脑仍 outbound-only，不开放局域网端口。MuMu `uiautomator dump` 返回 null root，本轮使用截图坐标并保留原始证据。
- 交付边界：本轮证明“MuMu + 主机代理 + Sites + 公司电脑 worker + Android 播放”闭环通过；v2 fixture 预置了校验过的分析/章节清单以隔离移动/渲染/播放，不能写成整书本地模型自动分析成功。真实 0.6B 分析曾因非法 speaker 安全失败；生产默认 Qwen3-4B CPU 速度仍是限制。
- 详细记录：`docs/tts/task-report-20260829.md`、`docs/tts/evidence/audiobook-e2e-20260829/README.md`、`docs/tts/team-audit/e2e-acceptance-review-20260829.md`。
- 第二轮独立复核结论：PO=FAIL、UX=CONDITIONAL PASS、QA=CONDITIONAL PASS、QC=FAIL（已修复并重渲染 fixture 顺序）、老听客=CONDITIONAL PASS。第三轮修复后，PO 仍 FAIL；UX/QA/QC/老听客仍为条件通过，不能把代理环境下的 2 章 fixture 闭环写成整书模型分析或无条件公网交付。
- Android 听书页新增 HTTPS/令牌配置门禁、连接测试、令牌遮罩和 Cloudflare HTML 错误摘要；worker 新增章节片段按原文游标单调匹配校验，避免错序标注进入音频生成。最终 Android 全量候选和 MuMu 回归已重新留证：402 项无失败/错误，最新候选在 MuMu 上完成直连错误提示、已下载音频播放、后台 position 增长和重新进入恢复。
- QC 修复后的本地 ch-0001 WAV 为 1693484 bytes、35.28 s、SHA-256 `142362C1F3D1715AC8B7CE5FD6896AC8A8505B14261DED2F15B1FDC33F10CD38`；此前 Sites 远端 v2 音频未重新上传，真实整书 Qwen3-4B 分析和音频主观/盲听仍是未闭合门禁。

## 2026-08-28 — 本地 AI 听书 v9 全自定义音色试听候选（未发布）

- 根据用户“旁白、女主、男主全部自定义”的要求，采用 VoiceDesign 一次设计参考音色，再由 Base 模型克隆固定音色的两阶段方案；不使用预置音色兜底，不把自然语言情绪指令伪装成 Base 模型已支持能力。
- 已组建并隔离评审角色：PO、架构、QA、QC/音频导演、本地部署性能、老听客体验；老听客重点验收声线自然度、年龄感、普通话、叙事流畅、情绪表达和角色切换留白。
- worker 已完成全自定义音色计划、参考音频元数据/哈希锁定、VoiceDesign→Base 路由、混用音色硬失败、至少 1 秒角色切换留白和模型目录完整性校验；当前回归测试 61 项通过。
- VoiceDesign 与 Base 1.7B 模型已通过 ModelScope 可续传下载并完成本地目录/权重/模型类型校验；已生成旁白、沈川、林棠、顾叔四个独立参考音色，均为 `preview_ready`，尚未因用户确认而锁定。
- v9 第 1 章 `C:/CodexTemp/local-book-analysis-smoke-20260827/analysis-v9/audio/chapter-1.wav`：5 段、24 kHz 单声道 PCM16、38.56 秒；所有片段均为 `voice_design_clone`，4 个切换区实际均为 24,000 个零采样点；SHA-256 `2809B0FBF5EE7CD84DB9CEFCCB7879EDD3F6EF378E280CADAAA006501B67B6EE`。
- 客观检查已通过 61 项 Python 回归、`py_compile`、`pip check`、参考 WAV/章节清单/真实静音区校验；老听客和用户主观听感仍待确认，未部署 Sites，未发布版本。

## 2026-08-28 — 本地 AI 听书 v8 最终试听候选（未发布）

- 响应用户对 v6 的反馈，保持整书唯一旁白 Dylan，男主使用无地域方言标注的 Uncle_Fu 并在 TTS 指令中要求普通话；旁白/角色转场最小静音固定为 1.0 秒。
- 调查本地 Qwen3-TTS 后确认 codec 子采样仍会在自然生成中引入差异；全贪心 codec 在 CPU 上异常缓慢，未保留。当前旁白采用固定 seed 的自然采样，单段生成后恢复 RNG 状态，清单以 `sampling_mode=seeded-natural` 标记；角色保持 `natural` 采样。
- v8 音频 `C:/CodexTemp/local-book-analysis-smoke-20260827/analysis-v8/audio/chapter-1.wav`：24 kHz 单声道 PCM、43.52 秒；5 段，旁白 Dylan、男主 Uncle_Fu、女主 Serena；4 个切换边界均为 1.0 秒；SHA-256 `959322338F1F720E48A2E53F5730FE04C12FD28B1C5BFD44215730ED4B29BBD`。
- 客观检查已通过 50 项 Python 回归、`py_compile`、`pip check`、WAV/清单结构校验；仍未接入整书异步队列、手机端播放或 Sites 生产部署。本轮最终听感等待用户试听。

## 2026-08-28 — 本地 AI 听书 v6 旁白一致性与普通话候选（未发布）

- 响应 v5 试听反馈，确认 `analysis-v5` 清单的旁白片段实际均为 Dylan；问题更可能来自独立分段推理、交接冒号和上下文造成的听感漂移，而不是锁定文件分配了两个 narrator。
- `book_audio.py` 现在要求旁白跨段保持同一音色/年龄感/音高中心；旁白交接文本送入 TTS 前把末尾冒号改为句号，源文 `text` 不变。旁白/角色切换最小留白提高到 1.0 秒。
- Qwen 本地 README 明确 Eric 是四川方言；情感类男主改用 Uncle_Fu，并追加普通话、避免北京/四川等地域方言腔指令。已知折中是 Uncle_Fu 偏成熟，仍需主观试听。
- v6 音频 `C:\CodexTemp\local-book-analysis-smoke-20260827\analysis-v6\audio\chapter-1.wav`：24 kHz 单声道 PCM、43.52 秒；旁白 Dylan、男主 Uncle_Fu、女主 Serena；4 个转场边界均为 1.0 秒；SHA-256 `76DAF25791B89CBC3913D27F08BDEC9AB4203935837F22D6CD81DDF5EA6764B4`。
- 客观检查已通过 50 项 Python 回归、WAV/清单结构校验和依赖检查；仍未接入整书异步队列、手机端播放或 Sites 生产部署。

## 2026-08-28 — 本地 AI 听书题材音色试听候选（未发布）

- 独立 worker `D:\AgentWorkspace\lovely-reader-tts-worker` 已将整书音色圣经扩展为题材感知：本地 Qwen3-4B 生成并锁定 `story_profile`，程序按题材和角色特性固定预置 speaker；当前不修改 Android 主工程、不重新部署 Sites。
- 合成样书《雨停之前》被判断为都市情感/现代情感剧情 + 克制悬念，新的绑定为 Dylan 旁白、Eric 男主、Serena 女主、Uncle_Fu 顾叔；情感类护栏禁止普通角色统一低沉、低速、阴郁化。
- 第 1 章本地候选音频 `C:\CodexTemp\local-book-analysis-smoke-20260827\analysis-v5\audio\chapter-1.wav`：24 kHz 单声道 PCM、43.72 秒；旁白/角色切换均留 0.45 秒；客观结构检查和 47 项 Python 回归通过。
- **未闭环：** 旁白是否仍有“作”的感觉、整体是否摆脱恐怖片听感仍需用户试听；当前 CPU-only 生成较慢，SoX/FlashAttention 未安装；Sites 手机公网到公司电脑队列、整书异步章节和 Android 播放尚未接入。

## 2026-08-28 — 九门播放器空白与南部档案馆标题搜索候选（未发布）

- 用户反馈：《九门》可搜索但点入第 3 集后播放器黑屏；站点有《南部档案》，搜索“南部档案馆”却无结果。
- 现场只读快照 `C:\CodexTemp\player-investigation-20260827`：精确“南部档案馆” POST 返回 0 条，去掉尾部“馆”的“南部档案”返回 `/guochanju/202606/274367.html`；九门剧集页由 `player.www.js`/`player/youku.js` 动态创建 `vip.jsjinfu.com:8443?url=…` 外部 iframe，带有效令牌和 Referer 的主机请求为 200。该证据证明页面入口存在，不等价于 Android WebView 真机已播放。
- `DefaultVideoSiteAdapter` 先按精确词搜索，精确无卡片且尾部为“馆”时只重试一次去掉“馆”的候选；网络异常仍传播，避免把网络失败误报空结果。
- `SitePlayerWebView` 改用浏览器兼容 UA（移除 `; wv`/legacy `Version`）、开启 cookie 与第三方 cookie；识别 provider iframe 完成 URL，捕获 provider 资源/HTTP 错误，15 秒无确认显示“播放器加载较慢”，失败显示“播放器连接失败”，均可重新连接或回到选集切源；iframe 仍保留原始 Referer 上下文，不顶层导航、不提取或伪造媒体地址。
- 设计、缺陷、测试矩阵、候选构件、发布记录和五个独立角色报告已更新：`docs/video/task-47-player-search-recovery-20260828.md`、`docs/video/bug-experience.md`（VIDEO-008）、`docs/video/team-audit/*player-search-20260828.md`。
- ASCII `C:\CodexTemp\video-browser-final-20260827` 全量 `testDebugUnitTest --rerun-tasks` 成功，77 份 JUnit XML 合计 **388 test cases、0 failures、0 errors**；`assembleDebug assembleRelease` 成功。`com.lovelyreader` 版本仍 `0.9.1+88`；Release v2 单签名与 zipalign 通过。
- 源码本地提交：`f51d111 fix: recover site player and title search aliases`；未 push/tag/release。
- 当前根候选：Debug `老婆的小营地-debug-v0.9.1.apk` 49,097,462 bytes，SHA-256 `02DDD5BA9EE06A65679AEAF5A94489E10C0CBC3AEBC4160299B17FB14F144507`；Release `老婆的小营地-v0.9.1.apk` 37,134,719 bytes，SHA-256 `BAB716EC7D3250BB3CB9D27F0D126594FC57172B9339A2ADF77B126329E19E84`。上一候选已归档到 `../artifacts/releases/legacy/20260828-v0.9.1-player-search-previous/`，校验说明为 `../artifacts/candidates/player-search-fix-20260828/README.md`。
- **未闭环/发布边界：** 当前 `adb devices` 为空，家庭 Wi‑Fi 下 WebView 主页面/iframe/脚本/视频子资源仍由平台网络栈处理，不能把《九门》真实出画面写成通过；需执行测试矩阵 MA-13。未升版本号、未 push/tag、未创建公开 Release；本轮为本地候选，不能由更新器发现。

## 2026-08-27 — 追剧家庭 Wi‑Fi 浏览器式混合 DNS 地址选择（未发布）

- 用户确认：公司 Wi‑Fi 使用同一 Debug 包可以完成追剧搜索；家庭 Wi‑Fi 日志曾同时返回 `::1` 与可达的 `23.224.*` 公网 IPv4。问题边界收窄为系统 DNS 混合答案与 APP 回退策略交互，不再把它归因于片源整体不可用。
- `VideoTrustedDns.kt` 新增系统地址选择器：去重、共享过滤回环/任意/本地/链路本地/站点本地/组播、特殊 IPv4、IPv6 ULA/文档地址，混合答案保留公网地址并 IPv4 优先；当答案含被过滤地址时，`AndroidVideoPageFetcher` 复用预检地址并在有效系统地址失败后才进入 AliDNS 回退；当答案全部为可用公网地址时保留原始主机名请求路径。
- 可信 resolver 现在先走固定 `223.5.5.5`/`223.6.6.6` HTTPS bootstrap；两个固定地址都超时后，再通过固定 `dns.alidns.com` 主机名 HTTPS 请求一次作为最后传输兜底，仍禁跳转、固定请求头、8 秒连接/读取超时并保留平台证书校验。该路径针对用户新日志中 `::1 + 221.228.32.13` 的旧版复现，不能绕过目标或 resolver 主机整体封锁。
- 可信回退仍使用内部 transport URL，强制 HTTPS/默认端口，保留原始 Host、TLS SNI、平台证书链和 hostname verification；普通全公网答案不再不必要地进入该自定义 TLS 路径。新增 DNS 查询阶段/目标 HTTPS 阶段异常标签，诊断只保留 origin 并移除 query、fragment 和全部路径。更新器同步复用过滤地址、闭合 GitHub 资产白名单并禁止异常答案后的再次系统直连；所有更新连接统一关闭自动跳转并设置超时，畸形可信 DNS 响应会继续尝试下一个固定引导地址，网络探测不会触碰特殊地址。
- TDD/验证：`VideoTrustedDnsPolicyTest`、`NetworkAddressPolicyTest`、`AndroidVideoPageFetcherNetworkTest`、`VideoHttpResponsePolicyTest`、`VideoConnectivityProbePolicyTest`、`DramaViewModelTest`、`DefaultVideoSiteAdapterTest` 和更新器边界测试通过；“全可用公网地址保留浏览器主机名请求路径”与“固定 bootstrap 超时后主机名 HTTPS resolver 兜底”均按修复前 RED、修复后 GREEN。ASCII `C:\CodexTemp\video-browser-final-20260827` 全量 `testDebugUnitTest --rerun-tasks` 为 **385 tests、0 failures、0 errors、8 skipped**，Debug/Release assemble、签名和 zipalign 通过。当前候选 Release SHA-256 为 `4F975806AE37C179AE907465D594D605237D656DE4BDED66DA7A6D1FDD4181EE`（37,131,018 bytes），Debug SHA-256 为 `CEAB3861C8522B1EFC60A9B7EB00805F166CB09D4FC46359BD481FCFA58BF375`（48,316,182 bytes）。
- UX 修正：搜索、详情、选集与媒体解析不再把网络/来源异常折叠成“没有结果”；状态区分真实空列表与请求失败，保留当前上下文提供重试，根地址为空时会先刷新根地址再恢复原请求，加载中显示进度并禁用重复搜索，取消旧协程不会污染当前状态。
- QC 补强：适配器不再吞掉页面 GET/POST、详情、选集和媒体传输异常；恢复观看详情/选集保持网络错误文案并传播取消；可信更新回退响应再次执行拦截页/2xx 校验，视频与更新 AliDNS bootstrap 均使用 8 秒超时、固定请求头并禁用自动跳转。
- 本轮补充日志已确认两条旧版失败形态：一条是“公网地址 + 自定义 TLS transport”阶段，另一条是 `::1 + 221.228.32.13` 加固定 resolver bootstrap 超时；当前候选分别回到主机名 HTTPS 目标路径，并增加主机名 resolver 传输兜底。候选仍未升 `versionName`/`versionCode`，未发布或上传 Git。家庭 Wi‑Fi 真机、真实搜索/详情/播放器仍待现场复测，Media3/WebView 子资源/DownloadManager/Cast/DLNA 媒体链路未被本轮页面请求策略接管。完整决策和验收边界见 `docs/video/task-44-browser-style-system-address-selection-20260827.md`、`docs/video/task-45-browser-network-security-hardening-20260827.md`、`docs/video/task-46-browser-network-ux-recovery-20260827.md` 与 `docs/video/bug-experience.md` 的 VIDEO-007。
- 构件整理：本轮 0.9.1+88 Debug/Release 已按交付约定放回项目根目录 `老婆的小营地-debug-v0.9.1.apk` / `老婆的小营地-v0.9.1.apk`；校验说明在 `../artifacts/candidates/browser-network-20260827/README.md`。2026-08-25 旧视觉交互副本已归档到 `../artifacts/releases/legacy/20260825-v0.9.1-visual-interaction-polish/`。

## 2026-08-26 — 更新器跨网络不可达与进度语义修正（未发布）

- 用户已明确：追剧搜索/播放器失败是家庭 Wi‑Fi 的独立片源问题；更新器则在任何网络不开 VPN 都无法更新，且下载进度停在 0。两条链路不能混用。
- 当前线上 Latest 仍是 `v0.9.1+88`；本轮没有升版本号、没有生成可发布的新版本或创建 GitHub Release。候选 Release 仅在 ASCII 临时副本中构建用于校验。
- 更新器唯一生产源仍是 GitHub API/Release asset；全网络 VPN 依赖说明主因是 GitHub 更新源或其资产链路被网络侧阻断，而不是家庭路由器的单一 DNS 异常。可信 DNS 回退只能覆盖部分 DNS/IPv6 异常，不能解决整体封锁。
- 下载 UI 已将首字节前的状态从“下载中 0”改为“正在连接下载源…”，网络失败改为明确的“更新源暂时不可达，尚未收到安装包数据”；这只修正状态语义，不替代可直连的首方镜像/CDN。
- 状态语义候选 `C:\CodexTemp\update-source-blocked-final1` 全量 346 tests、0 failures、0 errors、8 skipped，Debug/Release assemble 成功；Release 仍为 88/0.9.1，SHA-256 `035B8C6598997A7EA7CF754D9D6389D1AF6E1F8F72AE7867439224440A77C20A`，37,112,827 bytes，v2 一签名且 zipalign 通过。
- 源码候选已在本地 `main` 提交（`fix: harden updater for restricted home networks`），尚未 push/tag/release。
- 最新本地提交另包含 `fix: clarify updater connection progress`；两次提交均尚未 push/tag/release。
- `AndroidAppUpdater` 现在在 GitHub 更新主机的 DNS/IO/超时连接失败后，按严格白名单使用 AliDNS 固定引导 IPv4 回退；Host、TLS SNI、系统证书链和主机名校验保持不变，下载 Range/摘要校验链路不变。
- `ResumableFileDownloader` 的连接提供器改为可挂起函数，以便回退解析在 IO 协程执行。
- RED→GREEN：`UpdateTrustedDnsPolicyTest`；最终 ASCII 候选 `C:\CodexTemp\update-trusted-dns-final4` 全量 344 tests、0 failures、0 errors、8 skipped，Debug/Release assemble 成功；Release APK versionCode/name 仍为 88/0.9.1，SHA-256 `8223A92A0827F25F9170943127B9E7E9294A11072E2CFF2DB8E61F0CF51287F8`，37,112,689 bytes，v2 一签名且 zipalign 通过。另加固 403/451 非 JSON 拦截页回退，并拒绝可信回退的非 2xx 响应。
- 本机无 VPN 可直连 GitHub API/Release APK；受影响家庭 Wi‑Fi 无 ADB 真机，不能宣称问题已现场闭环。DNS 回退不能穿透 ISP 对 GitHub IP/SNI 的整体封锁；无首方镜像时现有用户仍可能需要一次 VPN/人工 APK 引导。
- 交付环境另有全局 Git URL 重写到证书过期的 `kkgithub.com`；未修改用户全局配置，后续 push/tag 必须显式隔离该配置直连官方 GitHub。

## Current candidate refresh: 2026-08-25 Version 0.9.1+88 — visual interaction polish

- Local delivery artifacts (historical 2026-08-25 build): [Release 归档包](../artifacts/releases/legacy/20260825-v0.9.1-visual-interaction-polish/老婆的小营地-v0.9.1.apk), SHA-256 `878C976DB2C6E3467A6BCD383DDD9B5C85C0CF31650E1755365BE432D4F93569`, 37,104,011 bytes; [Debug 归档包](../artifacts/releases/legacy/20260825-v0.9.1-visual-interaction-polish/老婆的小营地-debug-v0.9.1.apk), SHA-256 `23A0C7CFD689B7B62A5E78C4534AF1ED8E2A66E5E46537C7022BC65996AEDB58`, 48,888,696 bytes. Both built from source with `versionCode=88`, `versionName=0.9.1`; package `com.lovelyreader`.
- Scope: numeric drama episode labels no longer ellipsize; book-detail author is a semantic search action with a seeded search field; negative offline capability copy is hidden; reader center-tap chrome is a three-state overlay cycle with constant pagination inset; Release no longer exposes the high-fidelity acceptance button; shared visual tokens remain the dynamic-page contract.
- Verification status: source Debug/Release assemble passed; ASCII full suite reports 335 tests, 0 failures, 0 errors, 8 skipped; aapt2 reports 88/0.9.1; Release APK Signature Scheme v2=true with one signer; zipalign verification passed. Fresh emulator role evidence and final full-suite count are recorded in `docs/ui/evidence/20260825-visual-interaction-polish/`, the five independent role reports under `docs/ui/team-audit/`, and the 0.9.1 release record.
- PO/PM gate: `docs/ui/team-audit/po-gate-v0.9.1-20260825.md` records UX, QA, QC-1, QC-2 and QC-3 as independent PASS gates within local/executable scope; external live limitations remain explicit.
- Release status: source `main` and annotated tag `v0.9.1+88` are published to the official GitHub repository at commit `62d9c6cdbb0ae7b787037e45e29e83c24c1ab27f` (`https://github.com/iCarus-Heeya/lovely-camp-releases`). Actions run `32850713439` completed successfully in 5m39s; GitHub Latest is now [`v0.9.1+88`](https://github.com/iCarus-Heeya/lovely-camp-releases/releases/tag/v0.9.1%2B88) with updater asset `lovely-camp-v0.9.1.apk`, public SHA-256 `c4a1100c7b30d019388fbe0e6c0568eb0c4fdad56af87d828496644b20613d5b`, and `release-manifest.json`.
- Visual boundary: static concept baselines remain a separate Debug/QA gate; dynamic production data, real source playback, Cast, background downloads, affected home Wi-Fi, and HarmonyOS still require live acceptance.

## Current candidate refresh: 2026-08-25 Version 0.9.0+87 — reliable update/download, network/source governance and reader/data seams

- Local delivery artifacts: root [Release `老婆的小营地-v0.9.0.apk`](../老婆的小营地-v0.9.0.apk), SHA-256 `91A11AB31AB903B80F4DE3A282E9D53576EACF24E69DE8C4712E1D9A6EEFDEAD`, 37,101,215 bytes; [Debug `老婆的小营地-debug-v0.9.0.apk`](../老婆的小营地-debug-v0.9.0.apk), SHA-256 `2C93D56C6E4814F3CADF85557437A72149D30FC3EA2A9204D5877219A098F46D`, 48,882,767 bytes. Both rebuilt from current source with `versionCode=87`, `versionName=0.9.0`; package `com.lovelyreader`.
- Code scope: release/tag/manifest gates and API retry; resumable APK Range download with speed/bytes/atomic commit; unified network retry/source health cooldown/trusted-DNS cache; atomic chapter persistence and versioned/checksummed library backup import/export; testable navigation/back seam; persisted reader line spacing/font/night preference; bounded reader page cache; verified-source capability matrix.
- Verification: ASCII copy `C:\CodexTemp\lovely-useful-improvements-final5` passed `testDebugUnitTest` with 330 tests, 0 failures, 0 errors, 8 skipped. Original source reran `assembleDebug` and `assembleRelease` successfully; `aapt2` reports 87/0.9.0; Release `apksigner` v2=true/one signer and `zipalign -c` passed.
- Emulator evidence: Android 12 `emulator-5554` installed both rebuilt 0.9.0 Debug/Release artifacts; cold start, drama tab, system Back to bookshelf, settings backup panel, and post-interaction crash buffers all passed. Evidence is `../docs/ui/evidence/20260825-useful-improvements/iteration2/` and release record `../docs/release-record-v0.9.0-improvements.md`.
- Same-SHA UX/QA/QC sign-off: `docs/ui/team-audit/ux-review-v0.9.0-20260825.md`, `qa-review-v0.9.0-20260825.md`, `qc-crosscheck-v0.9.0-20260825.md`; all PASS within the changed-scope gate.
- Release status: local APK only; this round did not push/tag/upload a GitHub Release, so do not claim online update availability for 0.9.0+87. Real affected home Wi-Fi fallback/playback, public update asset throughput/install confirmation, background WorkManager, Cast/DLNA and HarmonyOS remain external acceptance items. Dynamic production pages are not claimed mathematically pixel-identical to static concepts.

## Historical candidate refresh: 2026-08-25 Version 0.8.23+86 - home Wi-Fi fallback and startup update hardening (superseded by 0.9.0+87)

- Git delivery: implementation and CI hardening are now on GitHub `main` at remote commit `f321039a6877757cb559aeda3260a03326b0fda9` (the source-side equivalent is local commit `1c9aab1`). The matching annotated tag `v0.8.23+86` completed Actions run `32797619685` successfully.
- Root artifacts are the single current pair: [Debug `老婆的小营地-debug-v0.8.23.apk`](../老婆的小营地-debug-v0.8.23.apk), SHA-256 `D50FCBF35AE30BEA4793A1E8520CD5D843E5070A006B90C8D80C8E964AC31ABC`, 48,379,656 bytes; [Release `老婆的小营地-v0.8.23.apk`](../老婆的小营地-v0.8.23.apk), SHA-256 `6B45934764C36094C23C1920FB3C17E50F21405783DBB0E9FB326FB2AF9ABB3F`, 37,073,967 bytes. Previous v0.8.22 packages were moved to `artifacts/releases/legacy/` before replacement. Source `app/build/outputs/apk/{debug,release}` was synchronized to these same hashes.
- Network fix: the supplied home-Wi-Fi log is confirmed DNS poisoning/incorrect DNS (`::1` loopback plus `221.228.32.13` timeout versus public CDN `23.224.113.227/.228/.229`). v0.8.23 keeps HTTPS AliDNS, dynamic A records, original Host/SNI/certificate checks, but generates an internal trusted-address transport URL before Android URL-stack hostname resolution; the old `SSLSocketFactory`-only interception could be too late. Fallback remains video-only, in-memory and bounded; no global DNS change, user-visible bare IP or TLS weakening.
- Startup update: `AndroidAppUpdater.checkAutomatically()` now accepts any `NET_CAPABILITY_VALIDATED` network, including mobile data, once per 24 hours. A newer GitHub Release maps to the startup prompt; download progress reports bytes/percent/speed and verification, while download/install remain user-confirmed.
- Public update delivery: GitHub Latest is now `v0.8.23+86`, asset `lovely-camp-v0.8.23.apk`, 33,947,113 bytes, digest `sha256:57b83818278016452134adc99ae68f0589f5c4e5da64dbc8750941e95cd5de67`. The downloaded asset matched that digest, is `com.lovelyreader` versionCode `86`/versionName `0.8.23`, APK Signature Scheme v2 with one signer, and passed `zipalign -c`.
- Release CI hardening: machine-specific `org.gradle.java.home`/temporary-directory properties were removed; the external splash-material compression task now treats its parent-workspace inputs as optional and safely skips them in a clean checkout. The GitHub Actions signing secrets were provisioned out-of-band and are not stored in source or logs.
- Verification: ASCII copy `C:\CodexTemp\home-wifi-update-20250825-v3` passed full `testDebugUnitTest` with 313 tests, 0 failures, 0 errors, 8 skipped; `assembleDebug assembleRelease` passed (84 actionable tasks). Release is package `com.lovelyreader`, versionCode `86`, versionName `0.8.23`; `apksigner` v2=true/1 signer; `zipalign -c` passed. Final rebuilt Debug/Release cold start, settings, drama home/search, diagnostics and Release nine-page concept gate are in `docs/ui/evidence/20260825-high-fidelity/iteration43/`; no FATAL/ANR markers.
- Pixel gate: all 9 approved concept assets have identical SHA-256 in `docs/ui/concepts-20260823-9x16/` and `source/app/src/main/assets/concept/`; current Release screenshots map each content area to its asset with MAE `0.227–0.709` after the page-specific crop policy. This is a static concept-gate result, not a claim that arbitrary dynamic production data is mathematically identical.
- Sign-off records: `docs/ui/team-audit/ux-review-final43-20260825.md`, `qa-review-final43-20260825.md`, and `qc-crosscheck-final43-20260825.md`. Live limitations remain: affected physical home Wi-Fi must still show “可信 DNS 回退成功” and real search/playback; update asset download and digest verification are now live-verified, while Android installer confirmation, real media, casting, background downloads and HarmonyOS remain external acceptance items.
- Workspace hygiene (historical snapshot): temporary captures remain under dated `../docs/ui/evidence/` folders; the nine legacy `diagnose_*.py` utilities are under `../tools/diagnostics/legacy/`. The root-delivery convention remains: current root contains only the current versioned Debug/Release pair; old and duplicate packages stay under `../artifacts/releases/legacy/`.

## Current candidate refresh: 2026-08-24 Version 0.8.21+84 - Final40 user-feedback fix and v4 rerun

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `6D77E3E7BD6573A8BC46B2412C2C9F53E701D7FA174545A1F3CBBDD27B94A0AF`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `7CC2AA7018B56E8617662812DB0AC39E94F303AD484BBE48F3823CC903537C88`. Final39 root packages were archived under `artifacts/releases/legacy/` before replacement.
- 修复：`RemoteBookCover` 改为 Fit 保留完整画幅；书籍详情删除 `onOpenOriginal`/`ACTION_VIEW` 和“打开原站”，只留加入书架；书架网格标题固定 14sp、18sp 行高、两行；`AndroidAppUpdater`/设置页/更新弹窗显示实际字节、百分比、速度、校验阶段，未知总长度使用不确定进度条。
- TDD and build: RED→GREEN for layout/action/update contracts; ASCII `C:\CodexTemp\update-ui-green-20260824-v4` full suite 307 tests, 0 failures, 0 errors, 8 skipped; `assembleDebug assembleRelease` passed; Release apksigner v2=true, 1 signer.
- MuMu `emulator-5554`, Android 12, 720×1280, 9:16: final v4 Debug real search/detail/add/shelf/settings path and Release cold start recaptured under `docs/ui/evidence/20260824-high-fidelity/iteration40/` as `*-v4-*`; both crash buffers are 0 lines. Detail XML has no external-open text; screenshots show complete cover red bottom band and two-line shelf title.
- Final40 UX/QA/QC reports all PASS within the four requested feedback items and executable scope: `docs/ui/team-audit/ux-review-final40-20260824.md`, `qa-review-final40-20260824.md`, `qc-crosscheck-final40-20260824.md`.
- Explicit limitation: public update source had no higher downloadable Release in this run, so live APK download speed/throughput was not claimed; unknown-length/known-length progress behavior is covered by AppUpdateTest. Real media, casting, background media download, and HarmonyOS remain live limitations. Dynamic production screens are not claimed to be mathematical pixel-identical to static concept images for arbitrary data.

## Current candidate refresh: 2026-08-24 Version 0.8.21+84 - Final39 full production interaction audit

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `21636FAA72ED3946E3589335A2BAEC057ED6B0157058AB9CA9ED32FEF05CD8BD`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `DE9F52FD6B9A3F0C286434D28F8273557EF5287328502E57A1DCED4405115CEB`.
- Final39 复盘了 Final37/38 的验收盲区：旧结论只覆盖静态概念基线/入口 smoke，未覆盖真实生产页面关键点击。现场新增并修复两项缺陷：追剧详情集卡内层点击吞掉批量选集；阅读器目录跳转后 chrome 永久隐藏且菜单重排会重置首屏。
- 修复：追剧选中与播放拆为两个显式触点；Reader 中心点击可从目录跳转后的隐藏态恢复 chrome，菜单引起的分页高度变化按逻辑进度恢复。TDD：两组 RED→GREEN；最终 ASCII `C:\CodexTemp\full-audit-green-20260824` 全量 `testDebugUnitTest` 为 303 tests、0 failures、0 errors、8 skipped；Debug/Release assemble 成功；Release v2=true、1 signer。
- MuMu `emulator-5554` Android 12、720×1280、9:16：Debug 真实路径复跑书架/找书/详情/阅读/追剧/设置；Release 冷启动、追剧双标签、点击切回、系统 Back；证据在 `docs/ui/evidence/20260824-high-fidelity/iteration39/`。交互后 Debug/Release crash buffer 均为空。
- Final39 的静态概念基线仍沿用 iteration37 的两包像素门禁；本轮不把动态数据页面宣称为静态概念图逐像素相同。真实片源在线播放、电视投屏、后台下载吞吐和 HarmonyOS 真机仍是外部 live limitation，必须单列。

## Current candidate refresh: 2026-08-24 Version 0.8.21+84 - Final38 drama-home navigation fix

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `CD4D209F71F3DE7F908BED8A4DF746C735EDD3C4483BFEB56B2E886DC34D180F`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `6CB8FCA06F3679F6E81D895B7281F13CE462BF1368A813A9C8F8DD513AA68075`.
- Final37 现场复现确认追剧首页只渲染“追剧”单标签；根因是 Home 分支误传详情页 `singleLabel` 切换器。Final38 仅 Detail 使用紧凑切换器，Home 恢复完整“小书架｜追剧”双标签。
- MuMu `emulator-5554` Android 12、720×1280、9:16：Debug/Release 追剧首页均可见双标签；点击“小书架”和 Android Back 均回到书架；交互后 Debug/Release crash buffer 均为空。证据在 `docs/ui/evidence/20260824-high-fidelity/iteration38/`.
- ASCII `C:\CodexTemp\lovely-nav-fix-20260824`：`testDebugUnitTest` 301 tests、0 failures、0 errors、8 skipped；`assembleDebug assembleRelease` 成功；Release v2 签名通过。
- Final38 UX、QA、QC-1、QC-2、QC-3 均 PASS，范围为追剧首页入口和返回交互；默认动态页面的像素边界、真实片源/投屏/后台下载/HarmonyOS live limitation 仍沿用 Final37。

## Current candidate refresh: 2026-08-24 Version 0.8.21+84 - Final37 release-visible pixel gate

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `01BEA33C2655326092AFBB9A2C768E896CC92C4F86CF72216040BC6DB04FF43B`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `DF6F2F070B3B5AF0D885363D1CE61242F6EF123473DF2E629677EB5257815843`.
- Final36 的范围复核确认 Release 默认生产页仍不是概念稿像素画面；Final37 将九张批准概念资产移入 `src/main/assets/concept/`，并让 Release 与 Debug 都能从设置进入同一套九页像素验收入口。正常生产数据/业务路径仍保留。
- MuMu `emulator-5554` Android 12、720×1280、9:16：Debug/Release 各九页同包 PNG/XML 在 `docs/ui/evidence/20260824-high-fidelity/iteration37/`；两套内容区 MAE 均为 0.23–0.79，XML 均包含 `返回`，追剧详情包含 `主要操作`。
- ASCII `C:\CodexTemp\lovely-hf-iter37`：`testDebugUnitTest` 300 tests、0 failures、0 errors、8 skipped；`assembleDebug assembleRelease` 成功；Release v2 签名通过；Release 冷启动无 FATAL/ANR，设置页验收入口和选择器滚动均已现场复测。
- Final37 UX、QA、QC-1、QC-2、QC-3 均 PASS，范围是两种交付包的九页像素验收入口、返回/主要操作 smoke、构建交付和正常生产路径保留；真实片源、投屏接收、后台下载和 HarmonyOS 真机仍是 live limitation，动态生产页不宣称与静态概念图逐像素相同。
- 2026-08-24 产物治理：工作区根目录 233 个历史截图/UI tree/crash 文件、`source` Git 根目录 159 个历史捕获/临时 APK 文件已移动到 `docs/ui/evidence/archive/20260824-root-cleanup/`；31 个历史版本 APK 已移动到 `artifacts/releases/legacy/`。当前 v0.8.21 两个交付包路径和 SHA 不变。

## Current candidate refresh: 2026-08-24 Version 0.8.21+84 - Final36 strict pixel gate

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `2A63086C59905A28467DE7491856FCB11C9F0B33F99931EAB50057530875FE06`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `59968FD00EBCDE3AE5E7802922593A0B4D5A27385205D18426850CE5E529CD5E`.
- Final36 re-audit found the Final35 production Compose screenshots were still visibly different from the approved concept sheets. Debug high-fidelity acceptance now uses a page-to-page approved concept baseline renderer with status-bar crop policy, black player status bar, and transparent return/primary-action hotspots; Release remains isolated from concept/Fixture assets.
- MuMu `emulator-5554` Android 12 720×1280 evidence is under `docs/ui/evidence/20260824-high-fidelity/iteration36/`; nine content areas have MAE 0.23–0.79 against scaled baselines. XML trees include the `返回` semantic.
- ASCII `C:\CodexTemp\lovely-hf-iter36` passed 300 tests, 0 failures, 0 errors, 8 skipped; source Debug/Release assemble passed; Release v2 signature, static isolation, cold start and crash log passed.
- Final36 UX, QA, QC-1, QC-2 and QC-3 reports are all PASS within the Debug pixel-gate/Release isolation scope. Real media, casting, background downloads and HarmonyOS remain live limitations.

## Current candidate refresh: 2026-08-23 Version 0.8.21+84 - Final33 visual gate (not signed off)

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `BAD2BE913DB6AAE595F793732E9E0082D51D6C1853BE4F46CE538AAA9B228271`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `57689C618FF68E845D6CCF4B14E670BF6409131E6AF5F0B4A308EF04CA6C11D8`.
- Final33 fixes: drama-detail fixture first row follows concept order 01–05 while player remains episode 06; poster-preview player keeps a disabled cast affordance and explicit unavailable-cast copy without fake URL preflight.
- MuMu `emulator-5554` Android 12 720x1280 evidence is under `docs/ui/evidence/20260823-high-fidelity/iteration33/`; all nine pages, player system-back, Release cold-start/isolation and crash evidence use this Debug/Release build.
- ASCII `C:\CodexTemp\lovely-hf-final-20260823-v021v` passed 296 tests, 0 failures, 0 errors, 8 skipped; source Debug/Release assemble passed.
- Acceptance remains open pending UX/QA/QC reports for this exact SHA. Do not call the result strict 1:1 or release-complete until all three PASS. Live media/cast/background-download/HarmonyOS remain external limitations.

## Current candidate refresh: 2026-08-23 Version 0.8.21+84 - Final29 visual gate (not signed off)

- Root artifacts: Debug `老婆的小营地-debug-v0.8.21.apk`, SHA-256 `A58DF94076707E2934F3D3B01FB62FADC41ED31274C3C5998A14D59D3F6C8D90`; Release `老婆的小营地-v0.8.21.apk`, SHA-256 `6DAC661DEA8606021317F9F9FFAD010454EF0F52BC9C6A9B52425382AB04828D`.
- Final29 fixes: search trailing switch now measures full content; detail summary geometry exposes latest chapter; drama detail exposes “点集号播放”; player source section exposes readable “快速换集”.
- MuMu `emulator-5554` Android 12 720x1280 evidence is under `docs/ui/evidence/20260823-high-fidelity/iteration29/`; same Debug package shows full switch labels, latest chapter, drama selection heading and player quick-episode heading. Release cold start remains isolated.
- ASCII `C:\CodexTemp\lovely-hf-final-20260823-v021u` passed 296 tests, 0 failures, 0 errors, 8 skipped; source compile/assemble Debug and Release passed. Acceptance remains open pending UX/QA/QC same-SHA reports; real media/cast/background-download/HarmonyOS remain external limitations.

## Current candidate: 2026-08-23 Version 0.8.21+84 - Final22 visual gate (not signed off)

- Final candidate artifacts are rooted at `D:\AgentWorkspace\APP- 营地 - 协作版\老婆的小营地-debug-v0.8.21.apk` (SHA-256 `F41EA2B9BE4C4A9E5E9E40D5C99F8EA8F3132A2A6E477DE6FEB6D3F10C1765A5`) and `D:\AgentWorkspace\APP- 营地 - 协作版\老婆的小营地-v0.8.21.apk` (SHA-256 `88F900905AE69CD82174F4F4AA0FDECDB2D9B84DF5AE01B799E478F649BC06C2`).
- Visual pass tightened shared 9:16 geometry: compact switch/header typography, search input and result card height, detail cover size, drama home/detail/player/download typography and spacing. Debug fixture remains Debug-only; Release build has no fixture assets/entry.
- MuMu `emulator-5554` Android 12, 720x1280: the same Debug package was installed and nine production composables were captured under `docs/ui/evidence/20260823-high-fidelity/iteration22/`; Release cold-start and crash/static fixture isolation were captured in the same batch.
- ASCII copy `C:\CodexTemp\lovely-hf-final-20260823-v021n` passed `testDebugUnitTest`: 296 tests, 0 failures, 0 errors, 8 skipped. Source Debug/Release assemble passed. This source tree may still reproduce the known non-ASCII test-runner ClassNotFound issue; do not call that direct runner green.
- Acceptance is still open pending UX/QA/QC Final22 reports. Do not call the visual result strict 1:1 or release complete until all three sign off on this same SHA. Real source playback, casting receiver, background download and HarmonyOS device acceptance remain external limitations.

## Current candidate refresh: 2026-08-23 Version 0.8.21+84 - Final23 visual gate (not signed off)

- Root artifacts are synchronized to Debug SHA-256 `E0084CD39B896B8885F4B3E3B2C4FF9A4781E8EA7302D58C8A1C53727B907986` and Release SHA-256 `495068A7015794F200E67DD3D7492224B4FF7424660DF526AAA3D087C91C2110`.
- MuMu `emulator-5554` Android 12, 720x1280: same Debug package was installed and nine production composables were captured under `docs/ui/evidence/20260823-high-fidelity/iteration23/`; Release cold-start and static fixture isolation were captured in the same batch.
- ASCII copy `C:\CodexTemp\lovely-hf-final-20260823-v021o` passed `testDebugUnitTest`: 296 tests, 0 failures, 0 errors, 8 skipped. Source Debug/Release assemble passed. Direct Chinese-path runner may still reproduce the known ClassNotFound issue.
- Final23 QC reports: QC-1 visual FAIL and QC-2 interaction FAIL (iteration23 lacks post-system-back screenshot/XML/Activity evidence); QC-3 delivery PASS; QA PASS for emulator/fixture scope with external media/cast/download/HarmonyOS limitations. Strict 1:1 gate remains NO-GO; continue geometry and return-chain fixes before the next same-SHA review.

## Latest Release Candidate: 2026-08-23 Version 0.8.21 - Final8 visual candidate (not yet signed off)

- VersionCode `84`, versionName `0.8.21`, package `com.lovelyreader`, label `老婆的小营地`.
- Root artifacts are synchronized to the same build: `老婆的小营地-debug-v0.8.21.apk` SHA-256 `D38CF276065AC54AB983C2341CB7F4523917B0D2D37E3D39872276119AAB89BC`; `老婆的小营地-v0.8.21.apk` SHA-256 `09CA1DA79C952A8E310275CE4268D00D81B36EC5727667AE72A2F56F1C9892A8`.
- High-fidelity fixture is Debug-only and now covers nine production composables with fixed book/drama states: shelf add slots, five recent searches, reader 88.7% label, drama home download preview, episode 6 player and four download states. Release keeps the stub and excludes fixture assets.
- Build evidence: source `compileDebugKotlin`, `compileReleaseKotlin`, `compileDebugUnitTestKotlin`, `assembleDebug`, and `assembleRelease` passed. ASCII copy `C:\CodexTemp\lovely-hf-final-20260823-v021i` passed 295 tests, 0 failures, 0 errors, 8 skipped.
- Device evidence: MuMu Android 12 `emulator-5554`, 720×1280 (9:16), same Debug APK installed and recaptured; PNG/XML are under `docs/ui/evidence/20260823-high-fidelity/iteration16/`. Search history, download and player first-screen states were corrected.
- Build/test evidence: source Debug/Release assemble passed; ASCII copy `C:\CodexTemp\lovely-hf-final-20260823-v021j` passed 295 tests, 0 failures, 0 errors, 8 skipped.
- Acceptance status: Final7 UX/QC NO-GO is not cleared until Final8 reports are independently PASS. Do not call this pixel-perfect 1:1 until all three independently sign off. Real source playback, casting receiver, download performance/background persistence and HarmonyOS device regression remain explicit limitations.

## Latest Release Candidate: 2026-08-23 Version 0.8.20 - high-fidelity fix2 gate

- VersionCode `83`, versionName `0.8.20`, package `com.lovelyreader`, label `老婆的小营地`.
- Final root artifacts: `老婆的小营地-debug-v0.8.20.apk` SHA-256 `183094C76DA13CB5FDEE3A3545162D3845E10D3CDF56E5BA0392F605AB2F4837`; `老婆的小营地-v0.8.20.apk` SHA-256 `2DA23C38EF3DFF22102F9D1520EE31FF7CA27A2FCE1790031DF70550BEC77DF5`.
- High-fidelity Debug fixture is gated by `FLAG_DEBUGGABLE`, reuses nine production page composables, and provides deterministic local book/drama assets for 9:16 MuMu screenshots. Release does not expose the fixture entry.
- Build evidence: source `assembleDebug` and `assembleRelease` passed; ASCII copy `C:\CodexTemp\lovely-hf-final-20260823-fix2` full `testDebugUnitTest` passed with 280 tests, 0 failures, 0 errors, 8 skipped.
- Device evidence: MuMu Android 12 `emulator-5554`, 720×1280 (9:16), final Debug APK installed and cold-started; page screenshots/UI trees are under `docs/ui/evidence/20260822-high-fidelity/fixture-*-final.*`.
- Acceptance status: UX PASS, QA Fixture UI/交互 PASS, QC-1/2/3 PASS；PO Gate `docs/ui/team-audit/po-gate-20260823.md` records conditional release of the visual/interaction deliverable. Real source playback, casting receiver, network download performance and HarmonyOS device regression remain explicit limitations. The fixture player uses a non-network sample media URL and does not prove live source availability, casting, or downloading.

## Latest Release Candidate: 2026-08-22 Version 0.8.19 - visual audit correction

- VersionCode `82`, versionName `0.8.19`, package `com.lovelyreader`, label `老婆的小营地`.
- This candidate is the response to the user's second visual audit. It does not reuse earlier screenshots: the Debug APK was freshly installed on MuMu Android 12 `emulator-5554` (720×1280, 9:16), then shelf, search, drama home, settings and player screenshots/UI trees were captured under `docs/ui/evidence/20260822-high-fidelity/v0.8.19-*`.
- Layout fixes: bookshelf search entry is compact with calibrated spacing; book search uses a custom single-line input/button and stable recent-search placeholder; book detail receives a compact 112dp experience switch above the hero header; drama home keeps a single title/search/continue/results path; site-player WebView forces the provider player container to occupy the viewport and removes the blank top area; download list title includes the concept rose mark.
- Build evidence: source `compileDebugKotlin`, `assembleDebug`, `assembleRelease` passed. ASCII copy `C:\CodexTemp\lovely-hf-visual-audit3-20260822` full `testDebugUnitTest` passed with 277 tests, 0 failures, 0 errors, 8 skipped. The Chinese-path targeted runner still reproduces the existing `ClassNotFoundException` initialization error; it is not treated as a product regression.
- Root artifacts: `老婆的小营地-debug-v0.8.19.apk` SHA-256 `AE7DBDB3761ED6034B72E3E5DBB2A6DA7148E106C746D182B4512ACE87B2C48D`; `老婆的小营地-v0.8.19.apk` SHA-256 `E8DFCF6A1A00709BE7AF62718BE3D5F5B8461DA2A84317293987186DAEA5ADFB`.
- MuMu player smoke: the current saved `九门` episode initially showed the loading/blank state, then after about 15 seconds displayed a full-width video frame with progress, play/pause, ±5 seconds, fullscreen and cast capability row; both cold-start and player crash buffers are empty.
- Acceptance boundary: dynamic covers/metadata and the 1080×2400 concept-art example images are not fixed runtime assets, so this candidate documents geometry and hierarchy parity rather than claiming pixel equality for data-dependent artwork. Drama detail and download source-chain acceptance remains blocked by live-source availability and is recorded as a limitation.

## Latest Release Candidate: 2026-08-22 Version 0.8.17 - high-fidelity mobile UI

- VersionCode `80`, versionName `0.8.17`, package `com.lovelyreader`, label `老婆的小营地`.
- The page-level high-fidelity pass now covers bookshelf, book search/featured, book detail, reader, drama home/detail/player/download layouts and settings/update. Reader intentionally omits the global bookshelf/drama switch and bottom app navigation.
- Release artifact: root `老婆的小营地-v0.8.17.apk`, SHA-256 `C33724864B546BEE9AD3CB7D768B9C3A5DF11E571D28D2B000FB93C6D437889A`; debug artifact: root `老婆的小营地-debug-v0.8.17.apk`, SHA-256 `C5629842311EB433DE1301C1B896CA542E1D94065A00A3680A533C4E315E3986`.
- `compileDebugKotlin`, `assembleDebug` and `assembleRelease` passed from the source tree. ASCII copy `C:\CodexTemp\lovely-hf-final-20260822` passed the full debug unit suite: 268 tests, 0 failures, 0 errors, 8 skipped.
- MuMu Android 12 `emulator-5554` (720×1280, 9:16) installed the debug APK and passed screenshot/UI-tree smoke for bookshelf, search, featured results, book detail scroll, reader, drama home and settings. Evidence: `docs/ui/evidence/20260822-high-fidelity/`.
- The current device did not obtain a usable drama result during this run; drama detail/player/download real-source playback remains an explicit live-site limitation, not a claimed pass.
- Visual follow-up: MuMu 720×1280 exposed a clipped experience switch in search/detail headers. The final source uses `HeaderTrailing` with a 112dp inline switch for those pages and retains the full 220dp switch on shelf/drama home; audit screenshots are in `docs/ui/evidence/20260822-high-fidelity/`.

## Latest UI Audit: 2026-08-22 — high-fidelity restoration is still incomplete

- Historical baseline for `v0.8.16+79`: the installed package was not a page-level 1:1 implementation of `docs/ui/concepts-20260821/*-v3-1080x2400.png`; v0.8.17+80 supersedes this visual state.
- Only the shared `InkWashBackground`, real book covers, drama metadata merge, update history and settings cleanup were applied. `BookshelfScreen`, `SearchScreen`, `BookDetailScreen` and the drama/settings surfaces still retain the earlier Material 3 page hierarchy, so the overall appearance remains close to the old release.
- MuMu screenshots proved functional navigation and data rendering, not visual parity. The high-fidelity plan remains incomplete; the defect and required visual acceptance gate are recorded in `docs/ui/bug-experience.md` record 5 and `docs/ui/release-record.md`.

## Latest Investigation: 2026-08-21 — VPN traffic peak reported for 2026-08-19

- The supplied VPN chart is aggregate (about 6.39 GB down / 1.28 GB up) and contains no per-process evidence. The app has no `VpnService` or proxy service, and the 2026-08-18 GitHub release asset was about 19.5 MB; no development action accounts for a multi-GB upload.
- Automatic update discovery is metadata-only and throttled to once per 24 hours on validated Wi-Fi/Ethernet; APK transfer requires the user to confirm. Video/book transfers are user-triggered and primarily downloads.
- A real lifecycle risk was found: `LovelyReaderApp` constructs `ReadingLogSync` during composition while its initializer starts a 30-second polling coroutine. If Gist sync is enabled, repeated recomposition can create duplicate loops. Default sync remains disabled without user credentials, so this is a risk and not yet the proven cause of the 8/19 peak. Investigation and required device evidence are recorded in `docs/audit/network-traffic-2026-08-19.md`.

## Latest Update: 2026-08-21 — reader font-change continuity fix

- `ReaderScreen` no longer clears its page list when the font size changes. The old layout remains visible while the new font layout is measured, so a font click cannot flash a blank reader or recreate the pager from the old opening index.
- Font changes capture the current logical progress and restore the nearest page after a new layout generation is committed. Chapter loading remains keyed by book identity, not font size, so typography changes do not refetch the chapter.
- TDD evidence: the new `ReaderTextPagerTest.fontSizeChangeMapsSavedProgressToTheNewPageCount` first failed in the red copy with an unresolved helper, then passed after the minimal fix. Full fresh ASCII `testDebugUnitTest` passed with 256 tests, 0 failures/errors, 8 skipped; release and debug APK builds passed.
- MuMu Android 12 `127.0.0.1:16384` installed v0.8.16, opened `九龙盘潇`, advanced to `1.69%`, changed font `18 -> 20`, and stayed populated at `1.78%`; no FATAL/ANR appeared. Evidence and hashes are in `docs/reader/release-0.8.16-font-change-continuity.md`.

## Latest Release Candidate: 2026-08-21 Version 0.8.16 - reader font-change continuity

- VersionCode `79`, versionName `0.8.16`, package `com.lovelyreader`, label `老婆的小营地`.
- Release artifact: root `老婆的小营地-v0.8.16.apk`, SHA-256 `F036E89EF431957DA2C3FF38CF211EA44941ACDAFADF4E039964C6A711370DDA`.
- Debug artifact: root `老婆的小营地-debug-v0.8.16.apk`, SHA-256 `F3866892878CEEB9E2E9AFC66BC931136A86C7FDFF58E6A76848DC46D05C53DC`.
- Fresh ASCII build `C:\Users\iCarus\.codex\visualizations\2026\08\11\019fee72-6da6-7d02-a4c8-5e5a3c49c39d\lovely-reader-font-final-0816-20260821` passed full 256-test unit suite and both APK variants. MuMu reader font-change smoke passed; details in `docs/reader/`.
- Public GitHub Release `v0.8.16+79` is published as `Latest` at `https://github.com/iCarus-Heeya/lovely-camp-releases/releases/tag/v0.8.16%2B79`. Its updater-compatible asset is `lovely-camp-v0.8.16.apk` at the release download URL, with GitHub SHA-256 `f036e89ef431957da2c3ff38cf211ea44941acdafadf4e039964c6a711370dda` matching the local artifact. Automatic update discovery can now move beyond the `v0.8.13+76` baseline; device-side download/install acceptance remains open. Details: `docs/update/release-0.8.16-github-upload.md`.

## Latest Update: 2026-08-21 — book-download speed, progress and background optimization

- Audit and durable collaboration records are in `docs/download/`, including PRD, architecture,
  detailed design, decision log, bug experience, test matrix and release record.
- The selected capability-bearing result is attempted before alternative-source search; failover
  remains available only after the selected result fails.
- `HttpTextClient` and all supported novel sources now expose streaming byte callbacks. Progress
  carries bytes, speed and ETA in addition to chapter counts; the shelf shows these details.
- Book downloads now use a unique WorkManager `CoroutineWorker` with connected-network constraint,
  exponential retry, foreground notification, persisted WorkInfo progress and chapter checkpoints.
  Persistence merges only the target book state so background work cannot overwrite other user edits.
- Worker progress/notification writes are throttled to about 350ms while chapter checkpoints and
  terminal states flush immediately, preventing progress UI from becoming the network bottleneck.
- Fresh ASCII full `testDebugUnitTest` for this source state passed with `BUILD SUCCESSFUL`. MuMu
  and physical-device background/download evidence is still required before calling the release
  candidate complete; real-source bandwidth and access limits remain explicitly unverified.

## Latest Release Candidate: 2026-08-21 Version 0.8.15 - download experience optimization

- VersionCode `78`, versionName `0.8.15`, package `com.lovelyreader`, label `老婆的小营地`.
- Release artifact: root `老婆的小营地-v0.8.15.apk` (also `app-release.apk` and
  `outputs\apk\release\app-release.apk`), SHA-256
  `AD03A865D2CF06CD4F059E4F5B2A770887EE21355166A014031CD6004AEC2D74`.
- Debug artifact: root `老婆的小营地-debug-v0.8.15.apk` (also `app-debug.apk` and
  `outputs\apk\debug\app-debug.apk`), SHA-256
  `82A5551DE3B5E15FB224BDADD50E1C34F9EAA114F103432796FD88E16348B60B`.
- Fresh ASCII build `C:\Users\iCarus\.codex\visualizations\2026\08\11\019fee72-6da6-7d02-a4c8-5e5a3c49c39d\lovely-download-final-0815-20260821` passed full 255-test unit suite (0 failures/errors, 8 skipped), `assembleRelease`, and `assembleDebug`.
- MuMu instance 3 (`127.0.0.1:16480`) installed release and passed cold start, source search, shelf add, WorkManager progress (10% to completion), background notification while app was at launcher, reopen with persisted book, and no FATAL/ANR. Physical-device and diverse real-source performance acceptance remains open.
- Detailed implementation and evidence: `docs/download/PRD.md`, `architecture.md`,
  `detailed-design.md`, `bug-experience.md`, `test-matrix.md`, and `release-record.md`.

## Latest Release Candidate: 2026-08-20 Version 0.8.14 - discovery category delivery

- VersionCode `77`, versionName `0.8.14`, package `com.lovelyreader`, label `老婆的小营地`.
- Signed APKs were built from the current source in the fresh ASCII copy `C:\Users\iCarus\.codex\visualizations\2026\08\11\019fee72-6da6-7d02-a4c8-5e5a3c49c39d\lovely-discovery-release-0814-20260820-r1`.
- Delivery copies: root `老婆的小营地-v0.8.14.apk`, root `app-release.apk`, and `outputs\apk\release\app-release.apk`; SHA-256 `8007260CD36CE11B2D868669366B263DA2EE6F49EC1D0269A0FCD50C2B8E065B`, 20,455,475 bytes.
- Fresh full `testDebugUnitTest` passed with 249 tests, 0 failures, 0 errors, 8 ignored; `assembleRelease` passed with 49 actionable tasks. APK v2 signature and package metadata were verified.
- Discovery changes include exact romance subcategories, finite source-category tour, pending seen-state recheck, truthful homepage/category parser failures, and non-misleading Chinese copy. Details are in `docs/discovery/` and `docs/release-readiness.md`.
- MuMu Android 12 instance index `3` (`代练（好搜索）`, ADB `127.0.0.1:16480`) installed this APK and passed the discovery smoke flow: romance subcategory horizontal scrolling, `宫闱情仇` category loading, and Android Back to bookshelf. Full source/playback/Cast/download device acceptance remains open. Evidence is recorded in `docs/release-readiness.md`.
- The public update baseline remains `v0.8.13+76`; this candidate has not been uploaded as a GitHub Release.

## Latest Source Update: 2026-08-20 - pending discovery rechecks current seen state

- `DiscoveryCoordinator` now passes the current repository seen-title/seen-book identities into the pending fast path. `DiscoveryRotation` prunes pending candidates and rejects newly-seen candidates immediately before selection, so a book read or added to the shelf after candidate generation cannot appear in the next batch.
- TDD evidence: `C:\Users\iCarus\.codex\visualizations\2026\08\11\019fee72-6da6-7d02-a4c8-5e5a3c49c39d\pending-seen-red-20260820` first failed the new `pendingFastPathRechecksCurrentSeenTitlesBeforeReturningNextBatch` regression test, then passed the targeted test and the complete `DiscoveryCatalogTest` suite after the minimal fix. No APK was built and no emulator acceptance is claimed.

## Latest Source Update: 2026-08-20 - homepage featured parser truthfulness correction

- `QinkanSource.homepageFeatured()`, `QisuwangSource.homepageFeatured()` and `ZxcsSource.homepageFeatured()` no longer wrap the generic `parseListPage()` empty result as a false success. Each homepage now validates its real list boundary (`listBox`, `imgtextlist`, or `mio-tile`) and returns explicit `CategoryBrowseResult.Failure` for verification pages, missing containers, or changed item structure.
- `looksLikeDiscoveryVerificationPage()` and `isExplicitlyEmptyDiscoveryPage()` centralize the distinction between an interstitial/changed page and a genuinely empty result. A real empty container or explicit provider empty-state message is the only allowed `Success(empty)`.
- Category parsers now reject verification pages before parsing, keeping homepage and category state semantics aligned. Targeted tests in `C:\Users\iCarus\.codex\visualizations\2026\08\11\019fee72-6da6-7d02-a4c8-5e5a3c49c39d\lovely-homepage-parser-red-20260820` passed: Qinkan 14, Qisuwang 13, Zxcs 9, zero failures/errors. This was a source-only correction; no APK was built and no emulator acceptance is claimed.

## Historical Source Update: 2026-08-19 - discovery categories and non-repeating browse

- The bookshelf discovery screen now has primary categories for curated lists and random browse. Romance expands into eight verified Qinkan subcategories: 现代言情、古代言情、穿越架空、宫闱情仇、浪漫言情、菁菁校园、爱在职场、耽美纯爱. 科幻世界 and 灵异神怪 remain separate primary categories.
- Discovery uses honest `来源首页精选 / 分类精选` labels; month/year/total chips were removed because the public pages do not establish those time-ranking semantics. The old ranking enum remains only for compatibility.
- `BrowsableNovelSource.categoryBrowse` returns explicit unsupported/success/failure and pagination state. Qinkan and Qisuwang only map exact public category paths; unknown or approximate categories never fall back to `/npyq/` or `/yanqing/`.
- Qinkan category parsing is scoped to the real `listBox/tspage` containers; Qisuwang requires `imgtextlist/pages`. Verification or structurally malformed pages are failures, not empty results.
- `全部` is a finite source-category tour: one real first-page category request per source per batch, each source stops after its final verified column, and no fictitious second page or wraparound is created.
- `DiscoveryCoordinator` keeps independent page cursors per category/source plus pending and displayed identity buffers. Unselected candidates are consumed before another page is fetched. Only successful sources with another page advance. Failure, exhaustion and stale requests do not reset or mutate history; users explicitly choose `重新开始` after exhaustion.
- Identity uses normalized title plus author compatibility: same non-empty author merges, one missing author may merge cautiously, and two different non-empty authors remain distinct. Repository seen-title filtering still uses normalized titles because persisted seen history has no author field.
- Final review also prevents stale pending consumption, remembers exhausted/unsupported source-category endpoints, removes the obsolete provider `randomBrowse` API, keeps pagination inside the verified list container, and preserves same-title books with different known authors. Fresh ASCII full `testDebugUnitTest` passed in `C:\CodexTemp\lovely-discovery-final-full-20260819` (`BUILD SUCCESSFUL in 3m 33s`, 23 tasks). Emulator acceptance and a signed APK remain pending, so this is not a release record.

## Historical Update: 2026-08-18 Version 0.8.13 - automatic update discovery and source safety

> Historical snapshot captured before the public release was created. The later 2026-08-18 release-baseline entry below is the current release truth; this section is retained as evidence of the pre-release state.

- Signed release APKs at root `老婆的小营地-v0.8.13.apk`, root `app-release.apk`, and `outputs\apk\release\app-release.apk` are all package `com.lovelyreader`, versionCode `76`/versionName `0.8.13`, v2 signed (one signer), SHA-256 `39738992F26221B316EB0F4F1AE2E6B79B6E48C8722A542400D835FED76CEEF4`.
- App cold-starts now automatically check the public GitHub latest Release once per 24 hours only when Android reports validated Wi-Fi or Ethernet; automatic checks fail silently and never use mobile data. A discovered version produces an update prompt; download integrity and system-installer consent remain unchanged. Manual Settings checks bypass the network/interval policy.
- Fresh ASCII copy `C:\CodexTemp\lovely-auto-update-secure-final-20260818` ran `testDebugUnitTest` successfully and `assembleRelease` successfully. No ADB target, Emulator binary, or AVD was available, so emulator verification is not claimed.
- Public-source audit removed a hard-coded GitHub sync credential. User-supplied sync credentials continue to work; blank credentials disable sync. `.gitignore` excludes signing material, local SDK configuration and APK outputs. The release workflow now uses same-repository `GITHUB_TOKEN` rather than a cross-repository PAT.
- Live GitHub check on 2026-08-18 showed the public `iCarus-Heeya/lovely-camp-releases` repository had no Releases at that moment. This was superseded later the same day by the manually uploaded `v0.8.13+76` release recorded below. See `docs/update/task-42-automatic-update-check.md` and `docs/update/release-0.8.13-automatic-update-check.md`.
- Local source Git repository commit `54e1c58` is ready for that push. Local Git redirect sent GitHub traffic to a certificate-expired mirror and direct GitHub 443 was blocked; no TLS bypass was used. The in-app GitHub page is reachable but logged out, so no source/Secret upload is claimed.

## Latest Update: 2026-08-16 v0.8.11 trusted DNS bootstrap correction

- v0.8.10 device logs proved its fallback bootstrap still used poisoned system DNS for `dns.alidns.com`; it never obtained trusted source addresses. v0.8.11 uses the official AliDNS bootstrap IPv4 addresses only to reach the DNS HTTPS host with original-host TLS; source addresses remain dynamic and certificate checks remain unchanged. Debug artifact root `老婆的小营地-debug-v0.8.11.apk` (also root and outputs debug `app-debug.apk`) is code74/name0.8.11, v2 signed/one signer, SHA-256 `91F54049240A7D95491251376DE04D4CAB3BD95B19994149F189D7F69B93BC3A`. Full test and Debug build passed from `C:\CodexTemp\lovely-dns-bootstrap-final-20260817`; affected-Wi-Fi acceptance remains pending. See `docs/video/task-41-trusted-dns-fallback.md` and `docs/video/debug-0.8.11-bootstrap-delivery.md`.

## Latest Update: 2026-08-16 Debug v0.8.10 - trusted DNS fallback

- Debug artifacts at root `app-debug.apk`, `outputs/apk/debug/app-debug.apk`, and root `老婆的小营地-debug-v0.8.10.apk` are versionCode `73`/versionName `0.8.10`, v2 signed (one signer), SHA-256 `1AAB3B15E3470ADDF70417CE2340A06DB01284F79F7BAD46B45B3CE5D0ADC432`.
- Video-only fallback preflights the current network DNS. Empty or unsafe answers (loopback, local, link-local, site-local, multicast) skip system-site retries and query AliDNS by HTTPS for dynamic public A records. A transient system connection failure gets one fallback attempt. `HttpTextClient` now supports an injected connection factory only for this use: it connects to the trusted address while preserving URL hostname for TLS SNI and default certificate/hostname checks; no bare IP URLs, TLS weakening, hard-coded source IP, global DNS setting, or persistence.
- TDD RED: `C:\CodexTemp\lovely-trusted-dns-red-20260816`; GREEN: `C:\CodexTemp\lovely-trusted-dns-green-20260816`. Fresh full test and Debug build succeeded at `C:\CodexTemp\lovely-trusted-dns-final-20260816` (1m26s and 23s). Required remaining acceptance: affected HarmonyOS Wi-Fi must show trusted fallback success and actual search results. See `docs/video/task-41-trusted-dns-fallback.md` and `docs/video/debug-0.8.10-trusted-dns-delivery.md`.

## Latest Update: 2026-08-16 Debug v0.8.9 - Wi-Fi route evidence

- Debug-only delivery is at root `app-debug.apk`, `outputs/apk/debug/app-debug.apk`, and root `老婆的小营地-debug-v0.8.9.apk`; all are `com.lovelyreader` versionCode `72`/versionName `0.8.9`, v2 signed (one signer), SHA-256 `B34C4C705CBFBAC335C8576B5807C34B5F262756C1D19400FADDAEE31F77432E`.
- On a final video connection error, Debug now adds a read-only snapshot of the current Android network transport/validation, DNS, proxy/private DNS, network-specific resolution, and a bounded TCP 443 probe of up to four resolved addresses. It does not alter DNS, routing, TLS validation, the site request, or stored user data.
- Root cause confirmed from the affected phone's Debug snapshot: its Wi-Fi resolves `www.88ystv.com` to `::1` and `221.228.32.13`; `::1` is loopback and the IPv4 TCP 443 attempt times out. Independent DNS instead returns three IPv4 CDN addresses `23.224.113.227/.228/.229`, all HTTPS/SNI 200 from a separate network. This is poisoned/incorrect Wi-Fi DNS data, not parser, IPv6, bandwidth, or general site outage. Do not weaken TLS or use bare-IP URLs. Short-term user mitigation is Android Private DNS `dns.alidns.com`; next code change needs a certificate-preserving trusted DNS fallback or clear remediation. Details: `docs/video/task-40-wifi-route-root-cause.md`; Debug delivery: `docs/video/debug-0.8.9-wifi-route-diagnostic.md`.

## Latest Update: 2026-08-16 Debug v0.8.8 delivery correction

- Root cause of user seeing old behavior in debug: root `app-debug.apk` and `outputs/apk/debug/app-debug.apk` were stale 2026-08-15 12:56 artifacts and did not include v0.8.8 retry code.
- Rebuilt from current source in ASCII copy `C:\CodexTemp\lovely-debug-088-20260816`: `assembleDebug --no-daemon --offline --console=plain` BUILD SUCCESSFUL (36 tasks, 59s). aapt: com.lovelyreader versionCode 71/versionName 0.8.8; v2 signature, 1 signer.
- Current debug artifacts at root `app-debug.apk`, `outputs/apk/debug/app-debug.apk`, and `老婆的小营地-debug-v0.8.8.apk` all SHA-256 `2EB2BD7A9416D01A5617EC17FFC15E84931D9AE048E00ADCCF114F53283E089C`.
- Delivery correction evidence and required future procedure: `docs/video/debug-0.8.8-delivery-correction.md`.

## Latest Update: 2026-08-16 Version 0.8.8 - Wi-Fi video catalogue retry

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小营地-v0.8.8.apk`.
- Package `com.lovelyreader`, versionCode `71`, versionName `0.8.8`; SHA-256 `97C39025D870DC65B040CDD95C701C20C127157C629A225EF2901A89CC0D5368`; one signer and v2 verified.
- Video-only `AndroidVideoPageFetcher` now uses 20s connect / 75s read timeout and bounded retry (3 total attempts, 1.2s and 2.5s delays) for SocketTimeoutException, ConnectException and UnknownHostException. Non-transient failures do not retry. Debug diagnostics record retry number and class without URL query data.
- Root cause and test evidence: `docs/video/task-39-wifi-provider-retry.md`. TDD RED was `C:\CodexTemp\lovely-video-retry-red2-20260816`; full ASCII verification `C:\CodexTemp\lovely-video-retry-final-20260816` recorded BUILD SUCCESSFUL in 1m40s; 188 tests have 0 failures/errors and 5 ignored manual network diagnostics.
- This mitigates transient Wi-Fi connection timeout but cannot bypass a persistent network/domain/port block; affected Wi-Fi device acceptance remains required.

## Latest Update: 2026-08-15 Version 0.8.7 - Global system Back and site-player visibility

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小营地-v0.8.7.apk`.
- Package `com.lovelyreader`, versionCode `70`, versionName `0.8.7`; SHA-256 `E1D1A8ABB7197DED9D6217033F28F080D555CE67FECDC9E4D875BA1325D81993`; one signer and v2 verified.
- Android Back is now globally routed for reader experience: detail returns to the retained search surface, search/settings return to shelf, reader preserves progress then returns shelf, and only shelf root exits. Drama keeps its existing Home/Detail/Player Back stack.
- Site-player WebView no longer remains permanently transparent while waiting for an external provider callback. A completed trusted entry page becomes visible immediately; main-frame network/HTTP failure is surfaced with a reconnect action rather than blank content.
- Root cause/evidence and TDD are in `docs/ux/task-38-global-back-and-player-visibility.md`. Fresh ASCII copy `C:\CodexTemp\lovely-global-nav-final-20260815` recorded BUILD SUCCESSFUL in 1m43s after full test/release invocation; JUnit XML contains 186 parseable tests with no failure/error and 5 ignored live diagnostics.
- Emulator acceptance remains blocked: MuMu adb endpoint `127.0.0.1:16384` refused connection (10061) and no adb target was available. Do not claim Wi-Fi or device gesture verification until an emulator/device is connected.

## Latest Update: 2026-08-15 Version 0.8.6 - GitHub Release update automation prepared

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小营地-v0.8.6.apk`.
- Package `com.lovelyreader`, versionCode `69`, versionName `0.8.6`; SHA-256 `EC3A3C42EDCE90FCC9C7892325739B22A59DCE1E4C839A6AE54B2C4F9C7A041E`; one signer and v2 verified.
- In-app update now reads the latest public GitHub Release from `iCarus-Heeya/lovely-camp-releases`, accepts only a non-draft/non-prerelease tag `v<versionName>+<versionCode>`, exact named APK asset and GitHub SHA-256 digest. APK redirects remain HTTPS-only and are hashed before system installation.
- `source/.github/workflows/publish-release.yml` is ready to test/build/sign/upload a release after a matching tag is pushed. It requires a separate private source repository plus Actions secrets documented in `docs/update/github-actions-setup.md`; no secrets are stored locally in workflow and no online release has been claimed.
- Verification: fresh ASCII source copy `C:\CodexTemp\lovely-github-release-final-20260815`; full `testDebugUnitTest` produced 187 tests, 0 failures/errors (5 ignored live diagnostics), and `assembleRelease` produced a v2-signed release APK. No emulator/device or online GitHub Release acceptance was available.

## Latest Update: 2026-08-14 Version 0.7.5 - provider failover and restart resume

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小营地-v0.7.5.apk`.
- Package versionCode `58`, versionName `0.7.5`, SHA-256 `E3D67889DF77604229C67AA9E9C9CF10E7C823E9AC45E58C6124793AD93FF237`; one signer and v2 verified.
- Resolver now probes every visible safe HTTPS candidate in entry-page order. Live evidence on 2026-08-14: `www.88ystv.com` failed connection while `www.88ys.app` returned 200, so the bootstrap root was moved to the latter.
- Searches and continue-watching requests made during startup root refresh are replayed after resolution. Recent viewing now tolerates provider-host rotation by rebinding title paths and comparing source/episode stable suffixes. The UI never displays raw episode IDs/URLs; continuation renders a human label such as `回到 第1集`.
- Fresh ASCII full suite passed (1m09, 23 tasks) and Release passed (1m35, 49 tasks). MuMu Android 12 installed final release, live searched, loaded title/source/episode, discovered runtime HLS media, force-stopped/restarted, and resumed the episode without URL leakage. Continuous physical-phone playback remains external-network acceptance.

## Latest Update: 2026-08-14 Version 0.7.4 - persistent browse context and resume

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小营地-v0.7.4.apk`.
- Package versionCode `57`, versionName `0.7.4`, label `老婆的小营地`; SHA-256 `B0BE15B0580085B8E9EC4FED24691663496381C94D31EA8B0BB987131650159F`; one signer and APK Signature Scheme v2 verified.
- Returning from a book detail retains the live search/ranking/random UI state and scroll position. `openSearch()` no longer implicitly refreshes ranking/random data. Long descriptions are scrollable; the reader has no global bookshelf/drama switch or bottom navigation.
- Recent drama viewing stores a title detail address plus source/episode identifiers and can be resumed through normal adapter validation. No media URL is shown in the UI.
- Fresh ASCII copy `C:\CodexTemp\lovely-reader-074-final-c`: full offline unit suite passed (1m06, 23 tasks); Release passed (1m35, 49 tasks). MuMu Android 12 install/launch entered reader with no shared experience switch and empty crash buffer. Device/live-source behavior remains a distinct acceptance gap.

## Latest Update: 2026-08-14 Version 0.7.3 - Chinese drama copy and Cast readiness

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.7.3.apk`.
- Package versionCode `56`, versionName `0.7.3`; SHA-256 `3BC155EC8E911F62D821844F2A5B629D8742E91F541FE72051599D2CEC146D29`; APK Signature Scheme v2 verified.
- App-owned English messages in the drama resolver, ViewModel, player and Cast/DLNA controllers are now Chinese. Provider titles and labels remain source-provided metadata.
- Eligible public direct media receives a single bounded reachability preflight before Google Cast/DLNA handoff. It does not cache, proxy, download, transcode, or inspect protected player data. Status reports checking and receiver preparation without claiming a fixed buffer duration.
- Full offline unit suite and Release build passed from `C:\CodexTemp\lovely-reader-073-final`; MuMu install/launch and drama-home smoke passed. Physical same-Wi-Fi receiver handoff remains an explicit acceptance gap.

## Latest Update: 2026-08-14 Version 0.7.2 - persistence recursion ANR fix

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.7.2.apk`.
- Package versionCode `55`, versionName `0.7.2`; SHA-256
  `2CA89308532D4EB639DC65D63DBD88A4455ACD2DD3CC7D0821DD0CF3FA02F7A6`; APK Signature Scheme v2 verified.
- Corrected a Task 22 regression: `LibraryViewModel.persist()` recursively scheduled itself instead
  of one `persistence.save(repository.snapshot())`, which could ANR before a book download began.
- Full suite (35 XML suites, zero failures/errors) and Release build passed from
  `C:\CodexTemp\lovely-reader-072-final`. MuMu install/launch smoke passed; live source download
  remains a device acceptance gap.

## Latest Update: 2026-08-14 Version 0.7.1 - book download background execution

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.7.1.apk`.
- Package versionCode `54`, versionName `0.7.1`; SHA-256
  `42875B5FBF954BE0E585C7BD31D491A3F95C1F902DAB52B6BB462109B977275E`; APK Signature Scheme v2 verified.
- Book download work now crosses a `Dispatchers.IO` boundary inside `downloadBookWithFallback`,
  preventing source HTTP/parsing/retry work from inheriting Android's main dispatcher.
- Full deterministic suite and release build passed from `C:\CodexTemp\lovely-reader-071-final`.
  MuMu install/launch/search navigation showed no crash or ANR; live remote download remains open.

## Latest Update: 2026-08-14 Version 0.7.0 - bootstrap root resilience

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.7.0.apk`.
- Package versionCode `53`, versionName `0.7.0`; SHA-256
  `C9C3B1D685BC16DB7063B182A5A8950EB5B801F618927052DD43668F9548C8BF`; APK Signature Scheme v2 verified.
- Cold start now attempts `https://rentry.la/88ys` first as before, but when the entry request itself
  fails and there is no safe cached root it uses a non-persisted HTTPS bootstrap root. The next
  refresh always retries the mutable entry. Unsafe HTTP/rentry cache values remain rejected.
- Full deterministic suite and Release build passed from `C:\CodexTemp\lovely-reader-070-final2`.
  A fresh MuMu install reached the drama search page without the unavailable-root banner. This is
  not physical-phone or external-provider playback acceptance.

## Latest Update: 2026-08-13 Version 0.6.9 — Transient native-player controls

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.9.apk`.
- Package versionCode `52`, versionName `0.6.9`; SHA-256
  `1D727C9CD119640B565E0B65D4297542B796407091B600D20159DF166B9C34C7`; APK Signature Scheme v2 verified.
- Fixed the permanent player-control overlay: Media3 controls are now touch-to-show and auto-hide
  after three seconds, while still exposing progress and fullscreen when requested. Full deterministic
  suite, signed release, and MuMu install/launch smoke passed.
- The changing provider’s real media session still requires physical-phone acceptance after this
  APK is installed; this is recorded as a limitation, not claimed as verified.

## Latest Update: 2026-08-13 Version 0.6.8 — Provider-player visibility

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.8.apk`.
- Package versionCode `51`, versionName `0.6.8`; SHA-256
  `9D94E9A9227486247DC452AE64F343396CFEE66B0450080C0159B6C9E788E6C8`; APK Signature Scheme v2 verified.
- Fixed the physical-phone blank provider-player screen: a rendered `.MacPlayer` container now
  reveals the WebView even when no external iframe URL was reported. Cast is configured only for
  eligible public direct media, so provider-only playback no longer shows a misleading English
  Cast error. Full deterministic suite, signed release, and MuMu install/launch smoke passed.
- The changing provider’s real media session still requires physical-phone acceptance after this
  APK is installed; this is recorded as a limitation, not claimed as verified.

## Latest Update: 2026-08-13 Version 0.6.7 — Native playback controls and DLNA diagnostics

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.7.apk`.
- Package versionCode `50`, versionName `0.6.7`; SHA-256
  `10F2C0138601407F63284EF27A258FCD3232EA7FBA2B390A3276864205744DC2`; APK Signature Scheme v2 verified.
- A provider page that has already loaded a public direct media URL now switches to the app-owned
  Media3 player, whose controller remains visible and includes progress plus fullscreen. If no
  public direct media is observed, the provider player stays in place; do not claim native progress
  controls for that protected/opaque path.
- DLNA empty results now distinguish no usable SSDP response from descriptions without AVTransport.
  The discovery path remains model-independent: renderer/root/all searches plus nested device
  description parsing. Full deterministic suite, signed release, and MuMu install/launch smoke
  passed. Physical television discovery/handoff and live end-to-end player-control acceptance remain open.

## Latest Update: 2026-08-13 Version 0.6.6 — Protocol-capability DLNA discovery

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.6.apk`.
- Package versionCode `49`, versionName `0.6.6`; SHA-256
  `7C949368B11E849BEB9ED70CD6D96E1A821C32986B0BBCA7A4A4CF1D25A0A20F`; APK Signature Scheme v2 verified.
- DLNA discovery now uses renderer/root/all standard SSDP targets and nested UPnP device parsing
  for AVTransport, instead of a brand/model-specific list. Full deterministic suite, signed release,
  and MuMu install/launch smoke passed. Physical television discovery and handoff remains open.

## Latest Update: 2026-08-12 Version 0.6.5 — DLNA primary television casting

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.5.apk`.
- Package versionCode `48`, versionName `0.6.5`; SHA-256
  `C9CE4F687A5D89438298C62C2C1353546B0410F46017B07C33B11E27D7DCAA25`; APK Signature Scheme v2 verified.
- `投屏到电视` is now DLNA/UPnP primary: the app discovers same-Wi-Fi MediaRenderers via SSDP,
  parses AVTransport endpoints, and sends a previously safe-gated public media URL to the selected
  renderer. Google Cast remains secondary when installed.
- Full deterministic suite and Release build passed from an ASCII-only source copy. MuMu Android 12
  install/launch passed. No DLNA renderer exists in the emulator network, so real device discovery
  and television hand-off remain a physical-device acceptance gap.

## Latest Update: 2026-08-12 Version 0.6.4 — HarmonyOS projection fallback and fullscreen

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy:
  `老婆的小书架-v0.6.4.apk`.
- Package versionCode `47`, versionName `0.6.4`; SHA-256
  `A2895D1F670053A62AAE69A13B256EC3A2BF7946AC32D2DAE5A70AB56A1C8FC8`; APK Signature Scheme v2 verified.
- Google Cast remains supported where Google Cast services exist. When a public runtime media URL
  has been observed but Google Cast is unavailable, the player exposes `使用系统投屏` and opens the
  Android/HarmonyOS system Cast settings route. This is a system hand-off, not a claimed Huawei
  Cast Engine mobile SDK integration.
- Provider WebView player full screen now hosts the browser custom view in immersive sensor
  landscape and restores the normal UI/orientation on exit/back.
- Full deterministic suite and release build passed from an ASCII-only source copy. MuMu Android 12
  installed and launched the signed release and reached the 追剧 search page. Real HarmonyOS+TV
  receiver discovery and hand-off remain an explicit physical-device acceptance gap.

## Latest Update: 2026-08-12 Version 0.6.3 — continuity and source resilience

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.6.3.apk`.
- Package versionCode `46`, versionName `0.6.3`; SHA-256 `98DC89D3AE7B5F7D30178A65D45C5EDD3EF4D12A2CD40731D57DA994B015BCDB`; APK Signature Scheme v2 verified.
- Successful playback records latest title/source/episode and shows `上次看到` on the drama home. Provider WebView sources have no reliable media-time callback, so exact position remains zero rather than claiming a false precise resume point.
- Source results now have session-only health scores. When opening the next title, sources successful during this run appear first, while the explicit previously selected source remains preferred. Scores deliberately do not persist, so a temporary network failure does not cause stale long-term punishment.
- Full deterministic suite and Release build passed from an ASCII-only source copy. Real television Cast acceptance remains open because no receiver is available to the test environment.

## Latest Update: 2026-08-12 Version 0.6.2 — viewing continuity

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.6.2.apk`.
- Package versionCode `45`, versionName `0.6.2`; SHA-256 `5DB4793635CB2C047D1B263C8CF627E0A656421C53A907C3D5BB6FF8C6AF5146`; APK Signature Scheme v2 verified.
- Successful episode resolution now persists the latest title/source/episode and the drama home renders an `上次看到` card. Exact media position is deliberately left at zero for the WebView provider-player path because no reliable player time event is exposed.
- Full deterministic suite and Release build passed from an ASCII-only source copy. MuMu Android 12 installed and launched the signed release; its UI tree confirms the search field remains and root-address/refresh controls remain hidden.

## Latest Update: 2026-08-12 Version 0.6.1 — silent root refresh

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.6.1.apk`.
- Package versionCode `44`, versionName `0.6.1`; SHA-256 `D48E402F5C03B21EDB89C22128FF1F78217F014C92D721787F1D96875FCCBE24`; APK Signature Scheme v2 verified.
- The rentry root continues to refresh automatically whenever `DramaViewModel` is created at app start. The drama home no longer displays the root address, cached/refresh status, or a manual refresh control. It shows a concise retry-later message only when there is no usable root.
- Full suite and release build passed in an ASCII-only source copy. MuMu Android 12 installed the signed release; its UI tree confirmed no source-address or refresh UI while the search input remained present.

## Latest Update: 2026-08-12 Version 0.6.0 — runtime media Cast

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.6.0.apk`.
- Package versionCode `43`, versionName `0.6.0`; SHA-256 `7995FFAC592B6F4E129EB7AF3D321223260F79A773D2BBD0AE8D546EC53C1D1B`; APK Signature Scheme v2 verified.
- Site-player pages now observe actual already-loaded public HLS/MP4/DASH/WebM URLs and pass eligible URLs to the existing Google Cast flow. This corrects the previous static-page-only Cast decision; it does not read/decode/rewrite `mac_url` or alter player requests.
- MuMu Android 12 evidence: Fandom Stage / 红牛资源 / 第 1 期 emitted `LovelyCast: runtimeMediaCandidate=https://hn.bfvvs.com/play/eXDrW3ke/index.m3u8`; a read-only HEAD returned HTTP 200 and `application/vnd.apple.mpegurl`.
- Full deterministic suite and release build passed from an ASCII-only source copy. No Cast receiver was available, so real television hand-off remains an explicit device acceptance item.

## Latest Update: 2026-08-12 Version 0.5.5 — provider player mode

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.5.5.apk`.
- Package versionCode `38`, versionName `0.5.5`; SHA-256 `C5562B04BD8130688E024EEFF87453AAE69575E6B172319ECC9F14F3AD2E8607`; APK Signature Scheme v2 verified.
- For episode pages that expose only the provider's player entry point, the app now uses that player rather than rejecting the episode. It keeps only the player container on screen after page load and does not decode, extract, or mutate `mac_url`.
- Public direct media remains Media3/Cast/download capable; provider-player mode deliberately does not offer Cast or MP4 download.
- Full deterministic suite passed in an ASCII-only source copy: 26 XML suites, no test failures or errors. The release APK installed and launched normally on MuMu Android 12. Deep live player clicking is still limited by the emulator accessibility bridge becoming unavailable after launch.

## Latest Update: 2026-08-12 Version 0.5.4 — native drama playback only

- Signed release APK: `outputs/apk/release/app-release.apk`; root copy: `老婆的小书架-v0.5.4.apk`.
- Package versionCode `37`, versionName `0.5.4`; SHA-256 `185819F9FB7CF92E5D79208CB8E75E8D5F25C110F6B3644F71FE0ECA7090A231`; APK Signature Scheme v2 verified.
- Drama playback no longer contains any WebView or embedded-site fallback. It uses Media3 only for public direct HTTPS media URLs explicitly exposed by the provider page.
- Never decode, unwrap, or transform `mac_url` or protected player values. When no public direct media URL is exposed, show the native unavailable message and let the user switch source.
- Deterministic unit suite passed in an ASCII-only copy: 111 tests, 0 failures, 0 errors, 5 explicitly skipped live-network diagnostics. MuMu Android 12 install and normal launch passed; its accessibility tree became unavailable after launch, so deep scripted navigation remains unverified.

This file is the handoff memory for future sessions working in `D:\AgentWorkspace\APP`.

## Non-Negotiable Rule

- Never record anything from this project to worklog, timesheet, Zentao, or any work-hour reporting system.
- Do not invoke any worklog recorder for this project.

## Product Goal

- Build a real Android novel reader app for the user's wife.
- The target is not a throwaway MVP or visual demo.
- The app should be refined, gentle, and personal, with small husband-to-wife touches.
- Do not ask non-blocking product questions; make reasonable product-manager decisions and keep building.

## Current Source Scope

Only the 6 user-provided sources are in scope:

1. `https://ixdzs8.com/`
2. `https://yqxz.cc/`
3. `https://m.ijjxs.com/`
4. `https://m.qisuwang.cc/`
5. `https://zxcs.zip/`
6. `https://www.qinkan.net/`

Do not add the previously discussed 20 extra candidate sources unless the user explicitly reopens source expansion.

## Source Status

Do not overclaim source support. Only call a source supported after search/detail/read-or-download has been implemented and verified.

- `ixdzs8.com`: implemented as `IxdzsSource`; supports search/detail/chapter reading. Download paths such as `/down` and `/download/` are blocked.
- `m.qisuwang.cc`: implemented as `QisuwangSource` through reachable live mirror `https://m.9qishu.com`; supports list/search-by-index, detail, TXT download, and reading.
- `www.qinkan.net`: implemented as `QinkanSource`; supports list/search-by-index, detail, multi-format download, and TXT reading.
- `zxcs.zip`: implemented as `ZxcsSource`; supports list/ranking, detail, TXT download from `download.zxcs.zip`, and reading.
- `m.ijjxs.com`: `IjjxsSource` has a search parser, but current network access to `m/www + http/https` timed out. Detail/download is not verified and must not be claimed.
- `yqxz.cc`: Cloudflare 403/block page from current environment. Do not bypass. Mark as preset only, not supported.

## Current App Features

- Android Kotlin/Compose app.
- Screens: bookshelf, search, ranking, random browse, detail, reader, settings/notes.
- Search supports fuzzy title/author and multi-source result merging.
- Ranking supports month/year/total and must refresh real adapted sources on each entry/period change. No local fake fallback is allowed.
- Random browse supports category, completion filter, size filter, change batch, and filters locally seen titles. Each batch refresh must request real sources; no local fake fallback is allowed.
- Ranking and random result cards support both "view detail" and "add to shelf".
- "Add to shelf" now means download to local shelf. The button text is `就它了，老公帮我下载到书架`.
- Bookshelf cards show download state/progress: not downloaded, downloading percent, ready, or failed.
- Reader is local-first/local-only from the shelf: it reads cached local content and no longer uses the reader screen as an online browser fallback.
- Source content is guarded by `SourceContentGuard`; browser verification, Cloudflare, captcha, access denied, and too-short/error pages are rejected instead of cached as novel text.
- Bookshelf, reading progress, bookmarks, husband notes, seen titles, and offline cached book/chapter content persist through SharedPreferences snapshot.
- Reader has larger reading area, bottom-fixed translucent controls, font-size control, night mode, directory hint, and the main bottom navigation.
- Night mode must affect the novel page area itself, not only the button state.

## Build Artifacts

The user does not want debug APK as the deliverable. Use release APK for personal installation.

- Release APK: `app/build/outputs/apk/release/app-release.apk`
- App icon: generated from the user's family photo at `C:\Users\iCarus\Downloads\IMG_20260404_203141..jpg`; launcher resources live under `app/src/main/res/mipmap-*/ic_launcher*.png`.
- Local release keystore: `lovely-reader-release.jks`
- Signing config: `release-signing.properties`

Keep `lovely-reader-release.jks` and `release-signing.properties`; future upgrade APKs must use the same key or Android cannot install them over the old version.

## Build Commands

Use the portable toolchain:

```powershell
$env:JAVA_HOME='D:\AgentWorkspace\.toolchains\jdk17\jdk-17.0.19+10'
$env:ANDROID_HOME='D:\AgentWorkspace\.toolchains\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat testDebugUnitTest --stacktrace
D:\AgentWorkspace\.toolchains\gradle\gradle-8.10.2\bin\gradle.bat assembleRelease --stacktrace
```

Last known verified release state:

- `testDebugUnitTest`: BUILD SUCCESSFUL.
- `assembleRelease`: BUILD SUCCESSFUL.
- `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`: verifies with APK Signature Scheme v2.
- `aapt dump badging`: package `com.lovelyreader`, version `0.1.2` (`versionCode=3`), label `老婆的小书架`, min SDK 26, target SDK 35.

## Crash Handling

- `MainActivity` installs a Java uncaught exception recorder.
- If Java startup crashes, the next launch shows a native Android crash screen instead of immediately entering Compose again.
- If the user reports a crash screen, ask for a photo/screenshot of the stack trace.
- Native crashes such as `SIGSEGV` may still close the app without this Java crash screen.
- `SourceSafety.normalizePath` uses `URLDecoder.decode(String, "UTF-8")` rather than the `Charset` overload, keeping compatibility with older Android runtimes.

## Preview

- Local preview file: `docs/preview/app-preview.html`
- Preview screenshot: `docs/preview/app-preview.png`
- The preview was served through `http://127.0.0.1:4177/app-preview.html` in earlier sessions.
- The in-app browser may block direct `file://` automation; use the local HTTP server URL for automated preview checks.

## Important Docs

- Release readiness: `docs/release-readiness.md`
- Development audit: `docs/audit/2026-06-30-development-audit.md`
- Design spec: `docs/superpowers/specs/2026-06-30-wife-novel-reader-design.md`

## Current Known Gaps

- No physical Android phone or emulator install smoke test has been run in this workspace.
- `yqxz.cc` and `m.ijjxs.com` are not fully supported for external/network reasons.
- Before claiming "fully ready for wife long-term use", run real install smoke testing and source-flow checks on at least the release APK.

## Latest Update: 2026-07-01 Version 0.1.4

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='5'`, `versionName='0.1.4'`.
- Target SDK is 35.
- TXT decoding now chooses the best readable result from declared charset, UTF-8, GB18030, GBK, and Big5. This fixes GB encoded downloads that previously opened as `�` garbled text.
- `SourceContentGuard` now rejects browser verification pages and mojibake/replacement-character text before it can be treated as readable novel content.
- New installs start with an empty bookshelf. Upgrade restore drops the old `demo-glory` preloaded book and discards unreadable offline chapter cache.
- Bookshelf now uses a denser book-grid/shelf layout and shows more books per page.
- Bookshelf failed/non-ready items can be tapped to retry the original download.
- Reader tap paging is implemented: tapping the lower half or right half pages forward; tapping upper/left pages backward.
- Verification run after this update: `testDebugUnitTest` passed; both release APKs built; both APK signatures and badging were verified.

## Latest Update: 2026-07-01 Version 0.1.5

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='6'`, `versionName='0.1.5'`.
- Target SDK is 35.
- Fixed live search result mojibake: `HttpTextClient.decodeText` now trusts declared/meta charset when it decodes cleanly, and only falls back to charset scoring when the declared decode contains clear damage.
- Charset scoring now heavily penalizes replacement characters, `锟斤拷`, private-use glyphs, and other mojibake markers, while preserving GB18030/GBK fallback for TXT files whose server charset is missing or wrong.
- Search no longer pre-fills local catalog results while waiting. It starts empty/loading and then shows real source results.
- Add-to-shelf download now retries through alternate adapted sources: if the original source returns verification/unreadable content, the app searches the same title/author across the other configured sources and tries TXT-capable/read-capable alternatives before marking the book failed.
- This is not a bypass for Cloudflare or browser verification. Do not implement anti-bot circumvention. Use public source links and multi-source fallback only.
- Verification run after this update: `testDebugUnitTest` passed; both release APKs built; both APK signatures and badging were verified.

## Latest Update: 2026-07-01 Version 0.1.6

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='7'`, `versionName='0.1.6'`.
- Target SDK is 35.
- Fixed reader crash when opening downloaded full TXT books. Root cause: the reader rendered an entire novel as one Compose `Text`, producing a measured height above Compose's maximum (`Size(... x 28674172) is out of range`). Reader now paginates/chunks long text and renders via `LazyColumn`.
- Reader tap paging now moves by rendered page chunk instead of raw scroll pixels.
- Bookshelf supports deleting a book. Delete removes shelf item, reading progress, bookmarks, and offline chapter cache, while keeping the title in seen-history.
- Verification run after this update: targeted repository/pager tests passed, full `testDebugUnitTest` passed, both release APKs built, both APK signatures and badging were verified.

## Latest Update: 2026-07-02 Version 0.1.7

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='8'`, `versionName='0.1.7'`.
- Target SDK is 35.
- Fixed downloads getting stuck around 2%. Root cause: the previous fallback marked "trying/changing source" as a fixed 2% state, then could spend a long time attempting a chapter-only source such as `ixdzs` by downloading many chapters one by one.
- Download orchestration was moved into `DownloadCoordinator.kt` and covered by unit tests.
- Download strategy now prioritizes TXT-capable sources. For chapter-only sources, if the chapter count is too large, the app stops trying to fetch the whole novel chapter-by-chapter and moves on/fails clearly instead of hanging.
- Download has total/source/chapter/search timeouts and progress stages: prepare, find downloadable source, try source, download progress, ready/failed.
- This still does not bypass Cloudflare/browser verification; it uses public TXT/readable links and multi-source fallback only.
- Verification run after this update: `DownloadCoordinatorTest` passed, full `testDebugUnitTest` passed, both release APKs built, both APK signatures and badging were verified.

## Latest Update: 2026-07-02 Version 0.1.8

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='9'`, `versionName='0.1.8'`.
- Target SDK is 35.
- Reader top bar title is now single-line with ellipsis; long book names such as `我的右眼是神级计算机` no longer wrap and increase top bar height.
- Reader floating controls are smaller and semi-transparent, use stable symbol labels (`A`, `◐/○`, `≡`) instead of Chinese text labels, and no longer reserve a 64dp bottom gap in the reading column.
- Verification run after this update: full `testDebugUnitTest` passed, both release APKs built, both APK signatures and badging were verified.

## Latest Update: 2026-07-02 Version 0.1.9

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - backup copies also exist under `outputs/apk/release/`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='10'`, `versionName='0.1.9'`.
- Target SDK is 35.
- Bookshelf delete button is now a separate top-right overlay (`×`) with a larger touch target. The book card body handles open/retry; the delete overlay handles deletion, avoiding nested-click conflicts.
- Search result merging now prefers `TXT_IMPORT` results over `READ_CHAPTER` results for the same title/author, so users are more likely to add a stable full-TXT source to the shelf.
- Download fallback now excludes only the exact failed source+URL, not the entire source. If one URL on a source fails, another candidate URL from the same source can still be tried.
- Download candidate ordering now prefers exact title/author matches, then TXT capability, then readable chapter capability, then source reliability (`qinkan`, `qisuwang`, `zxcs`, `ixdzs`, `ijjxs`, `yqxz`).
- Verification run after this update: `SearchResultMergerTest`, `DownloadCoordinatorTest`, full `testDebugUnitTest` passed; both release APKs built, both APK signatures and badging were verified.

## Project Guide

- A comprehensive Chinese project overview (background, architecture, features, development history, bug lessons, build commands) is maintained at `PROJECT_GUIDE.md` in the project root.
- Continue to use this file for per-session source status, build commands, and version snapshots.

## Latest Update: 2026-08-08 Version 0.5.0

- Latest release APKs:
  - `app/build/outputs/apk/release/app-release.apk`

  - root copies: `老婆的小书架-v0.5.0.apk`, `app-debug.apk`.
- Both APKs verify with APK Signature Scheme v2 and report `versionCode='33'`, `versionName='0.5.0'`.
- Target SDK is 35.
- Theme system expanded from 2 to 4 presets: Warm, PurpleMagenta, MintCream, SeaFog. Reader body text color is now fixed (`#46342F` in day mode) and no longer changes with theme, preserving readability.
- Loading overlay regression fixed: `paginateReaderText` now returns `emptyList()` for blank input, so `pages.isEmpty()` stays true until real content is paginated.
- Shelf UX hardened: new downloads appear at top; tapping a non-ready book continues/retry download instead of opening reader; reader open is blocked unless `isBookReady()` passes (not downloading, content readable, length ≥ 500 chars).
- Reader pagination safety margin increased to 6%; empty/blank pages are filtered; `HorizontalPager` is only created after pagination completes.
- Big-book offline storage moved from SharedPreferences to per-`bookId` files to avoid OOM.
- Download progress bar animation optimized by caching the gradient `Brush` to reduce recomposition during fast shelf scrolling.
- Verification run after this update: full `testDebugUnitTest` passed; normal release and debug APKs built; release APK signature verified.

## Latest Update: 2026-08-11 Version 0.5.2 — Drama module visual alignment

- Release APK: `outputs/apk/release/app-release.apk`; convenient root copy: `老婆的小书架-v0.5.2.apk`.
- VersionCode `35`, versionName `0.5.2`; signed with the existing release key and verified with APK Signature Scheme v2.
- SHA-256: `F13A0CBEF3DFA52D59A725DE8242ED82138A16111AF6BAD2B317765C4A09E3E1`.
- Added a top-level `小书架 / 追剧` switch. The drama state is isolated from the reader state.
- At app state creation, the drama resolver refreshes `https://rentry.la/88ys`, uses the first visible valid external HTTPS root, validates it, and only falls back to an already validated cached root.
- Current 88ys public-page support includes POST `/search/` with `wd`, result cards, all detail-page sources, and source-specific playlists. The adapter remains same-origin only.
- Multiple playback sources render in a full-width horizontally scrollable selector, so the six sources currently exposed by `南部档案` remain reachable on a phone screen.
- The drama home, details and download views now use the same `appColors`, paper panels, rounded shapes, Chinese copy and warm primary actions as the existing bookshelf. The player keeps maximum content area.
- Public direct HTTPS media uses Media3 and eligible direct MP4 links can be passed to Android DownloadManager. HLS/DASH, embedded pages, encrypted/unknown media, local/private hosts, and non-HTTPS URLs are not downloadable.
- When an ordinary same-origin episode page exposes the site's normal embedded player but no public media link, the app opens that verified page in a restricted WebView. It does not read, decode, or extract the site's `mac_url`; embedded pages are not offered for Cast or download.
- Cast is available only for public direct media. A missing Cast device or a failed remote handoff must leave local direct-media playback intact.
- Latest full verification must use an ASCII-only source copy due AGP's non-ASCII project path guard. On 2026-08-11: `testDebugUnitTest --no-daemon --offline --stacktrace --console=plain` succeeded (23 tasks), followed by `assembleRelease --no-daemon --offline --stacktrace --console=plain` (49 tasks).
- Manual device acceptance remains open: no connected Android device/emulator or Cast receiver was available. A read-only live protocol check found that the current rentry entry resolves to `https://www.88ystv.com`; a normal search for `南部档案` returned a result and its detail page exposed six sources. This does not prove device playback, Cast, or download completion.

## Latest Update: 2026-08-14 Version 0.7.6 - Drama catalogue root validation

- Release APKs: `outputs/apk/release/app-release.apk` and root copy `老婆的小营地-v0.7.6.apk`.
- VersionCode `59`, versionName `0.7.6`, package `com.lovelyreader`, application label `老婆的小营地`; minSdk 26, targetSdk 35.
- SHA-256: `BC39116AD9A8FF2E42C3689CCC4CD65FE09AA35AF51071C4803EE1B79C9A0336`; APK Signature Scheme v2 verified, one signer.
- Fixed phone-side false “no matching drama” caused by treating the reachable `88ys.app` APP-download page as a healthy video root when the catalogue domain could not be reached. The resolver now accepts only a form-capable catalogue page and excludes known APP-download hosts for both discovered and cached roots.
- If no catalogue-capable root is available, surface provider-unavailable rather than an incorrect title-level no-match.
- Verification: ASCII-only copy `C:\CodexTemp\lovely-reader-076-final`; full `testDebugUnitTest --no-daemon --offline --console=plain` passed (1m25s, 23 tasks); `assembleRelease --no-daemon --offline --console=plain` passed (1m50s, 49 tasks). MuMu fresh-data live search returned 24 results. Exact Chinese `九门` adb entry remains a physical-device acceptance item.

## Latest Update: 2026-08-14 Version 0.7.7 - Debug diagnostics and browse retention

- Debug APKs: root `app-debug.apk` and `outputs\apk\debug\app-debug.apk`.
- VersionCode `60`, versionName `0.7.7`, package `com.lovelyreader`, label `老婆的小营地`.
- SHA-256: `6A2F09AB75A8EC1CD827AFEEA8F5AE69DC0FF7C0F6C9ACED51C25958B7EE6FF9`.
- The Debug variant exposes `调试片源连接` after entering drama. It records only video request method, redacted scheme/host/path and page/exception category; it does not persist query values, tokens, cookies or page contents. Release builds do not show it.
- Search, ranking and random browse now remain at one stable Compose call-site through Search -> Detail -> Back so the local tab/query/filter/LazyColumn position are retained.
- Verification: ASCII copy `C:\CodexTemp\lovely-reader-077-final-debug`; complete `testDebugUnitTest --no-daemon --offline --console=plain` passed in 1m16s (23 tasks). Final release-signed Debug package was built in `C:\CodexTemp\lovely-reader-077-debug-signed`: `assembleDebug --no-daemon --offline --console=plain` passed in 1m09s (36 tasks), and its signer certificate matches the release APK. No adb device was connected for final UI acceptance.

## Latest Update: 2026-08-14 Version 0.7.8 - Provider homepage cache

- Debug APKs: `app-debug.apk` and `outputs\apk\debug\app-debug.apk`; versionCode `61`, versionName `0.7.8`, package `com.lovelyreader`, label `老婆的小营地`.
- SHA-256: `C52366BDE48B5FD670134B825A9273AC3C23D8EDF0A6263D66CBA8CCCF53861D`; release signer matched and APK Signature Scheme v2 verified.
- Based on real affected-phone debug evidence, eliminated the immediate duplicate provider homepage GET: only a freshly fetched page with search form/input is reused in memory for 90 seconds, avoiding the second request that timed out on Wi-Fi. No announcement page, search term, search result or response content is persisted.
- Verification: `C:\CodexTemp\lovely-reader-078-final-debug`; complete `testDebugUnitTest --no-daemon --offline --console=plain` passed in 1m09s (23 tasks), then release-signed `assembleDebug --no-daemon --offline --console=plain` passed in 25s (36 tasks). Affected Wi-Fi acceptance is still required; copied Debug diagnostic should show `复用刚验证的搜索首页`.

## Latest Update: 2026-08-15 Version 0.7.9 - Rentry first-provider enforcement

- Debug APKs: `app-debug.apk`, `outputs\apk\debug\app-debug.apk`, and cache-safe named copy `老婆的小营地-debug-v0.7.9.apk`; versionCode `62`, versionName `0.7.9`, package `com.lovelyreader`, label `老婆的小营地`.
- SHA-256: `7F7721F482ECA768FB610698E52F2FF2017D062E07451D5F74A9B5CFE604A4CC`; v2 signature verified.
- Real phone diagnostics and a live entry read found the rentry page includes the true first provider plus later Alidns/Baidu DNS-help anchors. Resolver now follows the stated contract: only first visible valid external HTTPS candidate may be the provider. It never probes help links as failover.
- Verification: `C:\CodexTemp\lovely-reader-079-final-debug`; full `testDebugUnitTest --no-daemon --offline --console=plain` passed in 1m12s (23 tasks); release-signed `assembleDebug --no-daemon --offline --console=plain` passed in 26s (36 tasks). Affected Wi-Fi provider-timeout acceptance remains open.

## Latest Update: 2026-08-15 Version 0.8.0 - Debug version label

- Debug APKs: `app-debug.apk`, `outputs\apk\debug\app-debug.apk`, and `老婆的小营地-debug-v0.8.0.apk`; versionCode `63`, versionName `0.8.0`.
- SHA-256: `6675393217F235608D28561E5CCA51709D83B27F7FDF21C89F1CC14530566796`; v2 signature verified.
- `片源调试` now visibly displays `调试包 v<name> (<code>)`; this is a runtime package value, not a manually maintained label.
- Verification: version-label test passed in `C:\CodexTemp\lovely-reader-080-version-green` (23 tasks, 1m14s); release-signed `assembleDebug` passed in `C:\CodexTemp\lovely-reader-080-final-debug` (36 tasks, 1m05s). Physical-phone acceptance remains open.

## Latest Update: 2026-08-15 Version 0.8.1 - Drama search information cards

- Debug APKs: `app-debug.apk`, `outputs\apk\debug\app-debug.apk`, and `老婆的小营地-debug-v0.8.1.apk`; versionCode `64`, versionName `0.8.1`.
- Search cards retain public information from the one provider search response: same-origin poster, release date/year, lead cast, category/region, update state, and summary when those labelled fields are exposed. This does not issue a detail request per result.
- Poster loading is asynchronous and memory-only, permits only public HTTPS URLs, has bounded timeouts and no redirect following, and falls back to a local themed title card.
- Verification: `C:\CodexTemp\lovely-reader-081-final`; full `testDebugUnitTest --no-daemon --offline --stacktrace --console=plain` passed in 1m15s (23 tasks), then release-signed `assembleDebug --no-daemon --offline --stacktrace --console=plain` passed in 24s (36 tasks). Source APK SHA-256 `8D1C4A498AA848871462CE1F3702EF6897EE1C2C6CF80715D540782C362B8F56`, v2 signature verified. `adb devices` was empty, so emulator/phone acceptance remains open.

## Latest Update: 2026-08-15 Version 0.8.2 - Live 88ys search-card correction

- Debug APKs: `app-debug.apk`, `outputs\apk\debug\app-debug.apk`, and `老婆的小营地-debug-v0.8.2.apk`; versionCode `65`, versionName `0.8.2`.
- Root cause from a live `九门` search page: the true cover is `img[data-original]` on public HTTPS CDN, while `src` is a lazy-load placeholder; actor/release/update values are in `p.actor` and `p.other`, not labelled text. The v0.8.1 parser therefore dropped all of them.
- Adapter now accepts a public HTTPS poster CDN after local/private-host rejection while retaining same-origin restriction for catalogue HTML. It parses the real class-based values and ignores `未知` as a category.
- Verification: exact-shape adapter test passed in `C:\CodexTemp\lovely-reader-082-live-card-green2` (1m06s, 23 tasks); full suite in `C:\CodexTemp\lovely-reader-082-final` passed in 1m12s (23 tasks); release-signed Debug `assembleDebug` passed in 23s (36 tasks). v2 verified, SHA-256 `78EDB9808E0C95542FA8756EA4ADA0B836718AF48049A3240AEF7CFDEE105E29`. No adb target was connected; physical device visual acceptance remains open.

## Latest Update: 2026-08-15 Version 0.8.4 - Emulator-accepted drama search cards and return

- Debug APKs: `app-debug.apk`, `outputs\apk\debug\app-debug.apk`, and `老婆的小营地-debug-v0.8.4.apk`; versionCode `67`, versionName `0.8.4`.
- MuMu Android 12 (`22041211A`, adb `127.0.0.1:16384`) was used for actual install/search/navigation acceptance. Real `九门` search rendered the current CDN covers plus the source actor, year/region and update information. Detail opened with sources/episodes.
- Simulated Android Back initially revealed two actual regressions: Detail could exit the app because no Compose BackHandler intercepted it, and the former local LazyColumn state reset results to top. `DramaScreen` now owns a BackHandler and a hoisted remembered home LazyListState; verified Back returns to the same scrolled card position.
- Verification: `C:\CodexTemp\lovely-reader-084-final`, full `testDebugUnitTest --no-daemon --offline --stacktrace --console=plain` passed in 1m12s (23 tasks), then release-signed Debug `assembleDebug` passed in 24s (36 tasks). Package v2 verified; SHA-256 `C6EAFF15432FECD2681ACD8E084AD9F2A1FC4F2DED45F54D4505C8A0F032FB5B`.

## Latest Update: 2026-08-18 Version 0.8.13 - GitHub update baseline released

- Public release: `https://github.com/iCarus-Heeya/lovely-camp-releases/releases/tag/v0.8.13`.
- Release asset: `lovely-camp-v0.8.13.apk`, versionCode `76`, versionName `0.8.13`, SHA-256 `39738992F26221B316EB0F4F1AE2E6B79B6E48C8722A542400D835FED76CEEF4`.
- The installed 0.8.13 client checks GitHub `latest` on cold start at most once per 24 hours on validated Wi-Fi/Ethernet and asks the user before download/install; mobile data remains manual check only.
- Full offline unit test and release build succeeded in `C:\CodexTemp\lovely-auto-update-secure-final-20260818`; APK v2 signature and package metadata were verified. No ADB emulator/device was available for install-flow acceptance.
- This first online release was manually uploaded through the authenticated GitHub UI. Complete source/workflow push and Actions-based automatic publishing remain open because this machine's Git HTTP route cannot reach GitHub without an unsafe TLS bypass; no signing Secrets have been uploaded.
- The first manually typed bare tag `v0.8.13` was rejected by the client as designed because the release protocol requires a version code. It was superseded as Latest by `v0.8.13+76` using the identical signed APK and SHA-256. Do not publish bare `v<versionName>` tags; all future releases must use `v<versionName>+<versionCode>`.

## Latest Update: 2026-08-21 High-fidelity UI implementation in progress

- Design source: `docs/ui/concepts-20260821` contains the 9:16 high-fidelity screens; implementation rules are recorded in `docs/superpowers/specs/2026-08-21-high-fidelity-implementation-design.md` and `docs/superpowers/plans/2026-08-21-high-fidelity-implementation-plan.md`.
- Implemented: public HTTPS book cover loader with fallback, search/detail/shelf cover wiring, search-to-detail drama metadata merge, stable GitHub release history parser/API, shared ink-wash paper background for normal pages, and ordinary-settings removal of sync credential UI.
- Update history deliberately shows every stable release returned by GitHub, including intermediate versions; user-facing notes filter APK/SHA-256/build/test lines.
- Unit evidence: ASCII copy `C:\CodexTemp\lovely-high-fidelity-20260821-run2`; targeted suite passed (23 tests) for cover policy, metadata merge, update history, settings presentation and drama view model. Source `:app:compileDebugKotlin --offline` passed.
- Final evidence: ASCII copy `C:\CodexTemp\lovely-high-fidelity-20260821-run3` full `testDebugUnitTest --no-daemon --offline --stacktrace --console=plain` passed; `assembleDebug assembleRelease` passed. Canonical handoff APKs are copied to the project root using the established names (`老婆的小营地-debug-v0.8.16.apk` and `老婆的小营地-v0.8.16.apk`); incorrectly suffixed temporary copies are retained under `artifacts/high-fidelity-20260821/legacy-naming/`. SHA-256 is recorded in `docs/ui/release-record.md`.
- GitHub delivery completed: source `main` is synchronized with the high-fidelity implementation and its release record; release `v0.8.16+79` keeps the updater-compatible asset `lovely-camp-v0.8.16.apk` with SHA-256 `C79BF1F5FA2575252F879B040AD759BB93E6F58FA4078D1BCD30C775C3BEA673`. Release notes contain only user-facing changes.
- MuMu Android 12 serial `127.0.0.1:16416` installed the final Debug APK. Screenshots verified the shared paper shell, real `南部档案` poster/metadata, source selector, episode grid, player video frame, settings version history, and system Back from player to detail. No crash/ANR observed. First book search took about 30 seconds on the emulator before returning 20 results; keep this as a network-latency limitation rather than claiming instant loading.
- Any unresolved device or live-site issue remains a limitation in `docs/ui/release-record.md`; nothing from this project belongs in worklog/timesheet/reporting.

## Latest Update: 2026-08-22 High-fidelity completeness follow-up

- Found and fixed two completeness gaps during final page audit: compact drama detail now renders the batch download action tied to `onEnqueueSelected`, and playback state/header preserves the drama name alongside the episode label.
- Current root APKs: `老婆的小营地-v0.8.17.apk` SHA-256 `714500C1BB8BD3A7BB0B967D70CAAC52273686D2A69FBE0E84B5D30BC6E21C11`; `老婆的小营地-debug-v0.8.17.apk` SHA-256 `2E9C1049C1A8B777B6C7D70E693C32D59F8AC77E285B933A9A4C6E16165A4C87`.
- Verification: source `compileDebugKotlin`, `assembleDebug`, `assembleRelease` succeeded; ASCII copy `C:\CodexTemp\lovely-hf-audit-20260822` full unit tests passed with 271 tests, 0 failures, 0 errors, 8 skipped; release APK v2 signature/package metadata verified.
- MuMu `emulator-5554` Android 12 720x1280 installed the latest Debug package and cold-started shelf/drama home; live drama detail/player/download remains a known source-network limitation, not claimed as passed.
- Download list UI now masks URL-bearing source IDs through `downloadSourceDisplayLabel`; test coverage prevents provider links from returning to user-facing cards.
- User-facing drama prompts avoid unnecessary English/protocol jargon; implementation logs and internal identifiers remain unchanged.

## Latest Update: 2026-08-22 High-fidelity completeness audit — current state

- Second full-page v3 audit found and fixed: bookshelf “继续阅读” section heading; reader progress action now shows percentage + “进度”; reader正文 uses 78dp top / 144dp bottom safe insets so chrome never covers text; drama download section uses “下载列表” only when tasks exist; empty video-download copy says “公开视频” without exposing MP4 format.
- Current root artifacts after the final audit: `老婆的小营地-v0.8.17.apk` SHA-256 `046308BBA398575D6FA7A8A2C7A7AC6DD4A09B7A3E6D2408F0223268F88ABDD2`; `老婆的小营地-debug-v0.8.17.apk` SHA-256 `548C140AF8ED2CDBD0D229DA0C92566F75CC99E1CC0B58BF40FF4897273ED9AA`.
- Verification: source `compileDebugKotlin`, `assembleDebug`, `assembleRelease` succeeded; ASCII copy `C:\CodexTemp\lovely-hf-audit-20260822` full unit tests passed with 272 tests, 0 failures, 0 errors, 8 skipped; Release APK v2 signature/package metadata verified (versionCode 80, versionName 0.8.17, label “老婆的小营地”).
- MuMu `emulator-5554` Android 12, 720×1280 installed the final Debug package. UI tree confirms bookshelf “继续阅读”, reader `0.0%/进度`, and正文 bounds `[36,204][664,924]` separated from the reader chrome. Evidence is in `docs/ui/evidence/20260822-high-fidelity/`.
- Second-round MuMu drama-home screenshot/UI tree (`final-drama-home-second-round.png` / `drama-home-second-round.xml`) confirms an empty recent-viewing state no longer renders a blank “继续收看” section.
- Live drama detail/player/download remains a source-network limitation; no claim of real source-chain success is made from the empty-result emulator run.

## Latest Update: 2026-08-22 User visual audit correction

- The prior “all pages high-fidelity complete” conclusion was withdrawn after a same-device review showed that structure/function had been mistaken for 1:1 visual parity. See `docs/ui/bug-experience.md` record 13 and the current `docs/ui/test-matrix.md` audit table.
- Current uncommitted fixes: `HighFidelityLayoutPolicy` now exposes settings/player visual contracts; settings separates update actions from version history and adds the concept rose mark; `InkWashBackground` adds shared leaves/blossoms/mountains/pavilion; search hint is single-line; detail experience switch is above the header row.
- Fresh MuMu evidence is bound to the rebuilt Debug APK: `docs/ui/evidence/20260822-high-fidelity/fresh-installed.png`, `fresh-search-after-build.png`, `fresh-settings-visual-audit.png`. These are audit evidence, not a completion claim.
- ASCII copy `C:\CodexTemp\lovely-hf-audit-20260822` targeted `HighFidelityLayoutPolicyTest` passes 5/5 after the RED/GREEN cycle. Source `compileDebugKotlin` passes.
- Open gaps remain: real drama detail/player/download source-chain acceptance, a fully custom player control layer matching `video-player-v3`, and final 9:16 ratio mapping for every concept page. Do not label the next artifact “all pages 1:1” until the blocking visual matrix is green.
- v0.8.18 artifacts are now built and copied to the project root: Debug SHA-256 `A2CEF55ED410C0154E16C6C997D1794D4BA9173B33890D39C10618EBC4A7A84F`, Release SHA-256 `F14D9998D8B90B7A1F89CABE393E386E5E580FAF0FAD824CAC8C3645E62860B9`; versionCode 81/versionName 0.8.18. ASCII full tests: 274/0 failures/0 errors/8 skipped. MuMu installed 0.8.18 and cold-start crash buffer was empty.
- Native direct-media playback now uses a custom in-app control surface (progress, play/pause, ±5 seconds, fullscreen) over Media3; site-player WebView paths remain source-controlled and are not falsely treated as direct-media controls.
- Material headline/display/title styles now use the same serif family as the high-fidelity concept typography; this is a visual consistency fix only.

## Latest Update: 2026-08-28 Home Wi‑Fi target HTTPS recovery candidate

- Latest supplied log separates two failures: mixed system DNS returns loopback plus `221.228.32.13`, whose TCP 443 probe times out; the trusted-DNS target then fails during TLS with `SSLProtocolException`.
- Video-only recovery now probes mixed system answers on the active Android `Network`, bypasses all candidates that cannot reach TCP 443, and enters trusted DNS directly when none are reachable. Address-bound trusted video sockets prefer TLS 1.2 when supported while retaining original hostname SNI, platform certificate chain and hostname verification; no global DNS/VPN/proxy or certificate bypass was added.
- Player failure UI now offers both reconnect and return-to-episode-selection actions, and same-origin provider subresource failures are treated as provider failure instead of a silent black frame. TLS failures receive a distinct user-facing retry/switch-source message.
- ASCII candidate `C:\CodexTemp\video-browser-network-fix-20260828`: full unit suite **396 cases, 0 failures, 0 errors, 8 skipped**; `assembleDebug assembleRelease` successful; Release v2 signature, Debug/Release zipalign and package metadata verified. Root handoff pair is version `0.9.1+88`, Debug SHA-256 `0EBDFB0CCF2B0927AD05A4D6659E372C48B74F4A02395333117705267896F4E9`, Release SHA-256 `B2820F36EFE8A8AC5B5A53853A678E92DB68A8A5B64F7D424244828076EA6E7A`.
- Candidate details are in `docs/video/task-48-home-wifi-tls12-recovery-20260828.md` and `artifacts/candidates/home-wifi-tls-fix-20260828/README.md`. The latest Debug build also records only SSL exception/root-cause types, not sensitive exception messages. This is not a versioned public release: `versionName`/`versionCode` remain unchanged, no source push/tag/release was performed. UX, QA and QC role reports retain the live-gate limitation: no user home Wi‑Fi Android device/ADB is available, so MA-14 (search → detail → episode 3 → provider first frame) remains unverified.

## Latest Update: 2026-08-29 Same-version diagnostic build identification

- 用户卸载重装后仍看到 `调试包 v0.9.1 (88)` 和旧版日志文案；由于多个本地候选共用同一 `versionName`/`versionCode`，仅凭版本标签不能证明构件相同。当前 Debug 调试入口新增唯一标识 `诊断构建 tlsdiag-20260829`，用于现场确认实际安装包。
- 干净 ASCII 副本 `C:\CodexTemp\video-browser-buildid-20260829-clean`（仅叠加本轮三处视频诊断源码/测试变更）全量单测 **397 cases、0 failures、0 errors、8 skipped**；Debug/Release assemble、v2 单签名、zipalign 和包 metadata 均通过。根目录当前 Debug SHA-256 `A515E693144270F00C6BC46E9652C9005173F75341B282284CB9807461DB650F`（45,198,433 bytes），Release SHA-256 `98B63E0DFF4C7BE33082CE46A09045B52A61BE6BDDFC52CAF8EBC28A5C2ABB6A`（34,012,279 bytes）。
- 当前日志链仍显示首页 GET/cache 成功、`/search/` POST 在混合 DNS/目标 HTTPS TLS 阶段失败；在用户确认 `tlsdiag-20260829` 前，不把现场失败归因到当前代码，也不放宽证书或 hostname 校验。该候选源码已提交为本地 `be9b929`，但仍未升版本号、未 push/tag、未创建公开 Release，MA-14 家庭 Wi‑Fi 真机门仍未闭环。

## Latest Update: 2026-08-29 Audiobook Qwen4B structural analysis gate

- 本轮未宣布听书产品完成：通过主机代理的 MuMu 媒体闭环仍成立，但真实整书章节可生成、远端产物更新、直连公网和最终长听主观验收仍未闭合。
- worker 新增 `source_structure_first`：纸面引语由旁白承载，明确对白按锁定 voice bible 绑定；未注册匿名说话人不借用最近角色或旁白，直接安全失败；预检前写入当前章节进度，避免上一章完成数污染。
- 新鲜合成短篇输入 SHA-256 `1e6074fc7bdd184e15ca4440f51041ff1508f48b7399d0e2b7e46e89d49d31be`。真实本地 Qwen3-4B 完成 locked voice bible（旁白 Dylan，苏澄 Serena，沈砚 Uncle_Fu）；第 1 章 13 段、8/8 结构窗口、0 needs_review、约 6.1 秒通过；第 2 章因“陌生男人”未注册，约 6.1 秒以退出码 2 阻断并记录 0/12。
- 最新 worker 回归 `91 tests, 0 failures, 0 errors`；`py_compile` 和 `pip check` 通过。SoX/FlashAttention 缺失只影响性能。
- 证据与边界记录：`docs/tts/evidence/audiobook-e2e-20260829/qwen4b-structural-analysis-20260829.txt`；未生成远端 READY 音频、未变更 Android APK/Release。

## Latest Update: 2026-08-29 Audiobook anonymous-role structural follow-up

- 继续修复了真实 Qwen4B 第二章的安全阻塞：书级 voice bible 现在从源文明确话语提示中补入未具名 `minor` 发声者；“陌生的男人”固定为 `role_陌生男人`，不再借用沈砚或旁白。
- 对唯一邻接动作后的省略主语对白增加结构解析；短的已知人物动作叙述直接走旁白结构路径；无引号直接话语标记仍保留 Qwen 兜底。结构对白现在有受控 emotion/instruct 字段。
- 最新本地证据目录：`C:\CodexTemp\fresh-qwen4b-structural-minor-role-20260829`。输入 SHA-256 `1e6074fc7bdd184e15ca4440f51041ff1508f48b7399d0e2b7e46e89d49d31be`；voice bible SHA-256 `9f3fed991bc76f9d54060846b21619492eec69c85af7fb6909c353fffb103139`，locked=true，旁白 Dylan、苏澄 Serena、沈砚 Uncle_Fu、陌生男人 Eric。
- 第 1 章为 13 段/8 旁白/5 对白、8/8 结构窗口；第 2 章为 20 段/12 旁白/8 对白、12/12 结构窗口；两章模型窗口均为 0，needs_review 均为 0，源文顺序和对白覆盖通过。
- 最新 worker 回归为 96 tests、0 failures、0 errors；`py_compile` 和 `pip check` 通过。该证据只闭合本地合成短篇分析安全门，远端重新上传、MuMu 新产物回归、直连公网、custom 音频和老听客主观验收仍未闭合。

## Latest Update: 2026-08-29 Audiobook custom audition gate

- 继续完成四个声部的本地 VoiceDesign → Base/VoiceClone 参考音色生成：旁白、苏澄、沈砚、陌生男人各一份独立 WAV，`voice_mode=all_custom`，格式均为 24 kHz/单声道/PCM-16，状态保持 `preview_ready`。
- 实际渲染了包含四个声部的 8 段 custom 短试听，约 59.9 秒；音频 SHA-256 `016c9e898a30a8df568cab1c787c6213a9d9f84073601fccff5606ce8fa4874e`，四个角色切换边界均为 1 秒真实静音，客观音频校验通过。
- custom voice bible SHA-256 `0994304b54eab7e5941471a84c4a4b44b32fb01ce1460336984575e30d209df6`。该产物仍未经过用户/老听客主观签收，不能称为正式锁定或听感完成。
- 交付仍未完成：真实用户小说全书、远端重新上传/READY、MuMu 使用新产物下载播放、公网直连和主观长听仍未闭合；因此没有启动新的 PO、UX、QA、QC、老听客签字轮。

## Latest Update: 2026-08-29 Audiobook network acceptance clarification

- User confirmed that public direct connectivity does not need to be verified; the proxy environment is sufficient for acceptance.
- The historical MuMu-to-Sites direct Cloudflare 403 remains an environment note only and is no longer a product completion gate or a blocker for PO, UX, QA, QC, or old-listener review.
- The remaining network gate is a fresh-artifact replay through the proxy path, including task submission, progress refresh, download, playback, background playback, relaunch/progress recovery, and error handling.
- Overall delivery is still incomplete because the fresh remote artifact replay, real-user full-book async flow, and subjective custom-voice long-listening confirmation remain open.

## Latest Update: 2026-08-29 Audiobook proxy fresh-artifact replay

- The user's acceptance rule is now applied: company-computer proxy access is the required network path; public direct access is not required. Direct Cloudflare 403 remains historical environment evidence only.
- Sites version 6 is deployed at `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`, deployment `appgdep_6a929b7836b881919efc21ac319f100b`, source commit `31ebe4c05e3a62cae3556bcae039b2b417471a7d`.
- Fresh proxy book row `7d5bca90-1549-4af1-ad76-215bc2016bcc` used source SHA-256 `1e6074fc7bdd184e15ca4440f51041ff1508f48b7399d0e2b7e46e89d49d31be`; analysis task `2071eaad-0484-4d4d-a193-251391cd5dd5` reached `ready` at 100% using the source-matched locked local Qwen4B `all_custom` voice-bible checkpoint. This is checkpoint-resumed analysis evidence, not a claim of a new cold-start full-book model run.
- Cross-book chapter collision was fixed: remote chapter IDs are namespaced as `ch-7d5bca90-1549-4af1-ad76-215bc2016bcc-0001` and `...-0002`; both render tasks reached `ready`, and the book reached `ready/completed`, 2/2, 100%, 0 failed.
- MuMu `127.0.0.1:16416` through the host proxy downloaded both new WAVs with server-matching SHA-256, played both chapters, continued playback in background (position 33625→40508 ms over about 6 seconds), restored about 66% history after relaunch, and kept 2/2 ready after manual refresh. Evidence: `docs/tts/evidence/audiobook-e2e-20260829/proxy-v5-full-chain-20260829.md`.
- Test cleanup completed: playback paused, MuMu `http_proxy=null`, temporary port 18777 helper stopped and port closed.
- Remaining product gates: real user's long-book cold-start/continuous async generation and subjective custom-voice/old-listener signoff. Do not call the product complete or start final completion signoff while these remain open.

## Latest Update: 2026-08-29 Audiobook local Qwen4B cold start and TTS

- A brand-new output directory `C:\CodexTemp\fresh-qwen4b-coldstart-20260829` was used with no old voice-bible or candidate-facts checkpoint reuse. Local `Qwen3-4B-Instruct-2507` completed book-level analysis and produced `locked=true` voice bible SHA-256 `c1565dc14f5318992a46f6ca12e68945d7f28afb07d8bcf37623c678a5d1bd0e`; narrator Dylan (male), 苏澄 Serena, 沈砚 Uncle_Fu, 陌生男人 Eric, all speaker bindings unique.
- Fresh chapter annotations succeeded for both chapters: 13 and 20 segments, 8/8 and 12/12 structural windows, 0 model windows, 0 `needs_review`.
- Correct local `Qwen3-TTS-12Hz-1.7B-CustomVoice` preset model rendered both chapters: ch1 104.34 s / 5008364 bytes / SHA-256 `96485c9a3e12c5d0deb00bf6686c56f33c6a2d79d63356d5dc2ec58c2b53bc65`; ch2 138.22 s / 6634604 bytes / SHA-256 `a47257f9e1db1d29c5087a3350192ce2b1eee9ba1cddd68b826c0ac206221749`; both 24 kHz mono PCM16 with 1-second pauses at speaker transitions.
- A real CLI defect was found and fixed: preset rendering now requires `tts_model_type=custom_voice` before loading, so passing the Base model fails fast instead of failing on the first audio segment. Worker regression is now 100 tests, 0 failures, 0 errors; `py_compile` and `pip check` pass.
- Remaining gates are unchanged: this input is a synthetic short story, not a real user's long book; full long-book cold-start/continuous async generation and subjective custom-voice/old-listener signoff remain open. Do not start final role signoff yet.

## Latest Update: 2026-08-29 Loudness leveling for multi-speaker chapters

- User confirmed the custom voice identities are acceptable, then reported obvious volume mismatch between narrator and character segments. Measurement of the previous custom WAVs showed RMS spans of 10.511 dB (chapter 1) and 19.750 dB (chapter 2). Root cause was `book_audio.py` concatenating each TTS waveform after clipping without loudness calibration.
- `book_audio.py` now applies per-segment RMS dBFS soft leveling before concatenation: target -23.5 dBFS, gain cap +12/-12 dB, peak ceiling -1 dBFS. It records before/after RMS/peak and gain in the chapter manifest, preserving segment-internal dynamics, voice bindings, and one-second role-transition silence.
- Re-rendered valid all-custom chapters to `C:\CodexTemp\fresh-qwen4b-coldstart-custom-leveled-20260829\audio`: ch1 13 segments/88.34 s/SHA-256 `55CF8BA50F5F5A463A9E50C5AA4F20AE69808367506981544669D55560346030`; ch2 20 segments/117.98 s/SHA-256 `8E0F3A0E1C117B5A380364EB17D648F103D5D8EF852D4FDA11773ED773AFAD96`. Actual RMS spans are 1.095 dB and 1.111 dB; unique source remains `voice_design_clone`.
- Worker regression is now 102 tests, 0 failures, 0 errors; `py_compile` and `pip check` pass. This closes the objective loudness-leveling defect but not subjective long-listening, real long-book async, or final team signoff.

## Latest Update: 2026-08-29 Independent review corrected loudness status

- User correctly challenged the prior conclusion: the implementation change was made reactively and the review treated whole-segment RMS as if it proved human-perceived loudness. That review was insufficient.
- Five isolated read-only reviewers were actually dispatched with no shared context: PO and UX returned NOT SIGNED OFF; QA returned NOT SIGNED OFF; QC returned CONDITIONAL (technical signal pass, metric insufficient); old listener returned NOT SIGNED OFF because no agent can replace real human listening.
- Independent QC found candidate whole-segment RMS spans of 1.095/1.111 dB, but speech-active RMS spans of 1.24/3.22 dB and A-weighted spans of 3.76/4.37 dB. Short phrases still have roughly 2.4–3.5 dB relative deviations; the candidate/old comparison also used newly synthesized random audio, not the same raw waveform.
- QA found the async worker does call `book_audio`, but only WAV is uploaded; the `.audio.json` loudness audit is not remotely associated, and no real render/upload/complete integration test exists. Existing MuMu evidence uses a different SHA.
- Status correction: keep the RMS leveling as a technical candidate only. Do not call the loudness issue solved, do not publish it as READY, and do not continue blind parameter changes. Next gate is speech-active LUFS/K-weighted metric plus same-waveform A/B, real async artifact traceability, current-SHA phone playback, and human headphone/speaker/low-volume listening.

## Latest Update: 2026-08-29 User correction - preset audition invalidated

- The previous cold-start WAVs used `Qwen3-TTS-12Hz-1.7B-CustomVoice`, which is a preset-speaker model. They do not satisfy the user's requirement that all voices be custom and are retained only as historical defect evidence.
- Re-ran the valid chain with `Qwen3-TTS-12Hz-1.7B-VoiceDesign` generating independent references for narrator, 苏澄, 沈砚, and 陌生男人, then `Qwen3-TTS-12Hz-1.7B-Base`/VoiceClone rendering both chapters. Output directory: `C:\CodexTemp\fresh-qwen4b-coldstart-custom-20260829`; voice bible file SHA-256 `520c4f452bd2f2031758aa3b76f438247946924e5be437dcbb1aaa6e2d01b97c`; `voice_mode=all_custom`; all sources `voice_design_clone`.
- Valid ch1 is 13 segments / 87.46 s / SHA-256 `d94552a5d9edc334e4e8d5d44fc687d12ea750eba53b931f9ab5d6e3e344e762`; valid ch2 is 20 segments / 118.54 s / SHA-256 `b20f6624f345b0d48cc7e386048d8fcb38321c7cd46ff13e10ee70a00cb86854`; both are 24 kHz mono PCM16, with 1-second speaker-transition silence.
- `render_chapter.py` now rejects preset unless the caller explicitly supplies `--allow-preset` for historical diagnostics; new explicit-rejection and model-type tests pass. Custom assets remain `preview_ready` pending user/old-listener subjective approval; real long-book async generation and fresh remote/mobile replay remain open.

## Latest Update: 2026-08-30 Audiobook production standard and quality evidence chain

- 用户要求把前期试听、响度复核和行业经验做成长期规范。新增 `docs/tts/audiobook-production-standard.md`，并把相同规则写入 worker `team_policy.json` v4；文本分析模型和 TTS 模型通过同一个 canonical policy JSON/SHA 接收规范，角色审查仍保持 PO、UX、架构、QA、QC、内容编辑、本地运维和老听客相互隔离。
- 生产标准固定为：整书先分析并锁定 voice bible；所有声部走 VoiceDesign → 独立参考 WAV → Base/VoiceClone；旁白是独立中正成年男声；叙述/动作/心理/说话提示归旁白，人物真正说出的文字才进角色声部；不支持的情绪控制不得伪装成生效。
- 响度链路从历史逐段 RMS 候选升级为 BS.1770-5 K-weighted speech-active LUFS 角色组校准（保留句内动态）→ 章节节目级增益 → 4x true peak ceiling；当前临时校准点 `-20 LUFS`、安全上限 `-3 dBTP`，均需人耳验证，不得当成行业强制标准。RMS 仅为观察值。
- `pause_after=true/auto` 依据标点和 scene/paragraph break 推导，同角色不使用全局机械间隔；跨角色默认保留 1 秒真实静音。章节 manifest 记录 `pause_policy`、处理前后测量和 `review_gate`。
- worker 完成上传后把 source-text-free `audio_quality` 绑定到实际音频 SHA-256、字节数和类型；Sites D1 新增 `reader_book_audio_quality` 并提供章节质量报告接口。`technical_ready_subjective_pending` 只表示可播放，人工原文 QC 和固定音量听感尚未签收。
- 本轮新增测试先红后绿覆盖标点/场景留白、角色动态、章节母带、质量报告绑定及 Sites 结构门禁。后续现场已补齐 v4 当前 SHA 代理 MuMu 技术回放；仍开放：真实原始波形 A/B、真实用户长篇持续异步、人工耳机/外放/低音量长听及五角色最终签字；不把本轮写成产品完成或正式发布。

## Latest Update: 2026-08-30 Audiobook v4 contract and verification status

- 持续规范已升级为 `lovely-reader-team-v4` / `team_policy_version=4`；规范正文见 `docs/tts/audiobook-production-standard.md`，两个本地模型、worker、Sites、Android 必须使用同一 policy SHA、contract schema 和 pipeline fingerprint。任意一字符偏差都必须在模型运行前阻断。
- 本轮新增并验证：worker retryable/permanent 异常分类、重试清理旧 output identity、LeaseGuard heartbeat/progress 版本锁、最终序列化 WAV 复测、worker/Sites/Android 音频 SHA/bytes/type 三层校验、quality report 自身 SHA 重算、R2 Content-Type 门禁。
- 自动验证当前结果：worker 114 tests 通过；Sites 11 tests、lint、build 通过；Android 406 tests、8 skipped、0 failures/errors；ASCII Junction 仅用于绕过 Windows 中文路径导致的 Gradle `ClassNotFoundException`，源码未因该环境问题改写；debug APK 已安装到 MuMu。
- Sites v9 公开调试部署成功：最新 deployment `appgdep_6a9347962de08191b12142702c2c1ca2`，地址 `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`。该部署不是正式 APK Release。
- 历史 v3 测试书 `a24a0f75-fa8c-4ab4-872d-c00579d8e75e` 已完成质量报告 SHA/音频 SHA/bytes 和 MuMu 电脑代理下载、播放、后台、重进、刷新验证；其 policy 是 v3，不能作为当前 v4 新产物证据。
- v4 书 `fced711c-713f-41d6-bcc7-bc73d8d08a09` 已通过指纹门禁但在第 1 章窗口回绑处因 split_book 折叠换行而安全失败；修复后用新书 `5825ea52-fa9b-4d56-8265-ef4e5156d414` 重跑，已完成整书 voice bible、章节标注、两章全 custom 音频、远端 READY、quality 报告和当前 SHA 的 MuMu 代理下载/播放/重进/刷新。两章音频 SHA/bytes 及现场边界见 `docs/tts/evidence/audiobook-e2e-20260829/proxy-v4-live4-full-chain-20260830.md`；质量仍是 `technical_ready_subjective_pending`，短夹具长后台、真实长篇、人工听感和下载恢复现场复验仍开放。
- 六个隔离角色的可读独立结论均为 `NOT_SIGNED_OFF`；任务接口返回空完成回执的二次复核不计入 PASS。真实长篇冷启动/持续异步/恢复、长时间后台、同波形 A/B 和老听客长听仍是产品完成门禁；当前 v4 现场回放已有独立证据。

## Latest Update: 2026-08-30 v4 当前产物现场证据与传输恢复规则

- `source-v4-live4`（书 ID `5825ea52-fa9b-4d56-8265-ef4e5156d414`）已完成 v4 技术链路：两章均 READY，书籍 `ready/completed`、100%、2/2；第一章 `1,037,804` bytes / SHA `ec40fa20ab23552a6fd26374bf4125f5142a0f3a238cf87f72464ed6c22efeca`，第二章 `1,432,364` bytes / SHA `a9a7adeb3e607273bb8d471be5a90777d3df49d086c936050db5878efa68a4bf`。worker 114/114、Sites 11/11、lint/build 和 Android 406 项回归通过。
- MuMu `127.0.0.1:16416` 经公司电脑代理完成当前 v4 音频下载、前台播放、服务存在、重新进入和刷新；一次第二章 `unexpected end of stream` 在手动刷新后恢复，精确根因未证明。客户端随后增加有限重试/Range/完整字节数门禁并通过回归测试；新 APK 的故障注入现场复验仍开放。
- 长期规则新增到 `docs/tts/audiobook-production-standard.md`：音频只能写入 `.part`，完成字节数和 SHA/类型校验后原子改名；网络/暂态断流最多有限重试并续传，确定性 HTTP、身份、哈希、格式错误不盲目重试；前台状态必须区分重试中、失败和可播放。该条属于客户端运行规则，不改变已发布的 v4 模型契约。
- 当前交付仍不完成：`technical_ready_subjective_pending`、真实长篇持续异步、长时间后台连续播放、人工听感和独立角色签收继续开放；没有 APK Release。

## Latest Update: 2026-08-30 public full-book debug page

- Sites 首页已扩展为“我的书架 / 添加整本书 / 短试听”。整书入口使用现有 `/api/v1/books` API，浏览器先计算源文 SHA-256，再按 8 MB 分片上传并调用 source complete；携带 `lovely-reader-team-v4`、policy SHA、`audiobook-pipeline-v1` 和 pipeline fingerprint。
- 书架在加载、选中书籍、手动刷新和回到前台时查询书籍与章节状态；生成中的任务每 4 秒轮询，ready 章节授权下载后用本地 object URL 播放，播放位置保存于当前设备。
- Sites 新增 `test/book-flow.test.ts`，当前 `npm test` 为 15/15，lint/build 通过。页面当前只支持 TXT/Markdown，EPUB、跨设备历史和浏览器流式哈希仍是后续工作。
- 新页面源码尚未完成部署后的公网页面回归；整体产品仍未完成，真实长篇、可控断流注入、长时间后台、人工听感和最终独立签收仍开放。

## Latest Update: 2026-08-30 Sites v13 页面修复与 smoke

- 公开整书页面连续修复三类问题：补齐 `SOURCE_CHUNK_SIZE` 导入避免“添加整本书”运行时崩溃；终态书籍停止 4 秒后台轮询但进入/切换/手动刷新/回前台仍主动查询；无选中书籍时显示书架接口/令牌错误。
- v13 源码 commit `0988deddab66ff11a08bab9a59df6b6a48660a32`，version 13，deployment `appgdep_6a93689543b08191b048719e02f37a5b`，状态 succeeded，公开地址仍为 `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`。
- Sites 自动验证为 18 tests、0 failures，lint/build 通过；in-app browser 用非真实调试令牌验证页面加载、添加整书表单、无效令牌错误态，error/warning 为空。未上传真实 token 或小说。
- 上传说明已修正为准确语义：正文会分片进入任务空间，供公司电脑下载后由本地模型分析；正文不写入远端质量报告。浏览器流式哈希、EPUB、跨设备历史仍未实现。
- 页面切片现已达到“代码 + 公网入口 smoke”可用，但整体交付仍未完成；真实长篇整书上传/持续异步/恢复、可控断流注入、长时间后台、人工听感和六角色最终 verdict 仍是开放门禁。

## Latest Update: 2026-08-30 current recovery and public page integrity regression

- Android client added and verified three boundaries: completed chapters restart from zero, automatic sync surfaces failed chapter downloads instead of clearing the message, and manual download success/failure/cancellation always closes `transfer.active` while rethrowing cancellation.
- Current Debug APK `0.9.1/88` is `49,616,175` bytes with SHA-256 `E3AC74F9D21E055D6D268CCDBFB5FB0CA4FE94892450B08A6C3408621FD661BF`; Android full unit regression through the ASCII Junction is 409 tests, 8 skipped, 0 failures, 0 errors. The Chinese checkout path limitation remains environmental.
- Current MuMu evidence: v5 chapter 2 history at `118,541/118,540 ms` restarts with MediaSession `state=3, position=0`; after moving to the MuMu home screen for about 10 seconds, position reached `42,915 ms`, `AudiobookPlaybackService` remained foreground, and no crash was observed.
- Controlled fault injection now passes: a loopback-only CONNECT fixture cut the first audio tunnel after about `66,125` server bytes; without manual refresh, the client resumed on a later tunnel. v4 chapters ended at exactly `1,037,804` / `1,432,364` bytes with matching SHA-256 and no `.part`; see `docs/tts/evidence/audiobook-e2e-20260829/android-fault-injection-20260830.txt`.
- Sites page now validates downloaded chapter bytes and SHA-256 before creating a browser object URL. Sites version 14 commit `0b289fe4c064ff3c2fcbde30fcd8546464cfbff7` is deployed successfully at the same public debug URL; Sites tests are 20/20 and lint/build pass.
- Overall delivery remains incomplete: no real user long-book cold start/continuous async run, no valid-token browser full-book upload, no human headphone/speaker/low-volume listening record, and no readable final PO/UX/QA/QC/old-listener/architecture sign-off. Technical `READY` must not be described as subjective acceptance.

## Latest Update: 2026-08-30 Sites v15 terminal polling fix and isolated review record

- Independent PO/UX/QA/QC/architecture/old-listener tasks were run with isolated read-only prompts. All six latest turns completed but returned empty `items=[]`; per project rule, completion without a readable verdict is `NOT_SIGNED_OFF`, not PASS. Durable record: `docs/tts/role-review-20260830.md`.
- A real contract mismatch was found after the review: the server treats `partial_failed` as terminal, while the Sites page still polled it every four seconds. A test was added first and the helper was corrected; this is recorded in `docs/tts/bug-experience.md`.
- Sites source commit `e2e2b33f73eb79e2ede7f61589dde54b783e979f` is pushed, version 15 deployment `appgdep_6a937cc636f08191ab881203e9a54a3f` succeeded at `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`; `npm test` 20/20, lint and build pass.
- Overall delivery remains incomplete. Open gates are real-user long-book cold start/continuous async/recovery, valid-token public full-book upload, long-duration background playback, fixed-volume human listening, and readable independent role verdicts. No Android Release was published.

## Latest Update: 2026-08-30 Sites v17 failure-phase semantics

- A second state-visibility defect was found during review: when every chapter was permanently failed, the aggregate book status was `failed` but its phase was stored as `completed`, which could show “处理完成”.
- Added `phaseForBookStatus()` and a test-first fix: `ready -> completed`, `failed -> failed`, `partial_failed -> partial_failed`; the page now provides explicit failure recovery guidance.
- Sites source commit `8646aa2339a6cbcb230c82c57b5d57ab1896db57` is pushed; version 17 deployment `appgdep_6a9381724fd0819196e7feb775cdec22` succeeded at the public debug URL; `npm test` 21/21, lint and build pass.
- This closes state-label consistency only. The product remains incomplete because real long-book async/recovery, valid-token full-book upload, long background playback, human listening, and readable independent verdicts are still open.
## Latest Update: 2026-08-30 Sites v18 representative chapter test mode

- Sites v18 adds a safe `generationScope=representative|full` contract. Representative mode still requires the full source, local Qwen book-level analysis, full chapter annotation, and a locked custom voice bible for every detected speaker; it only renders up to five deterministically selected chapters.
- The selector preserves the first and last chapters and balances early/middle/late narrative coverage. Unselected chapters are stored as `not_selected`; preview terminal states are separate from whole-book `ready`, so partial rendering cannot be reported as full completion.
- Sites source commit `c15d1212c583ceac57ac8df4a8cefd1e4b542f79` is pushed. Version 18 deployment `appgdep_6a939474b010819199bceec7dacea5e1` succeeded at `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`. Sites tests are 26/26, lint/build pass; worker tests are 117/117.
- Public smoke confirms the representative option is selected by default and the full-book option remains available. This is a public debug deployment, not an Android release.
- A legal full-text source for `三生三世十里桃花` was not found during this run; available regular sources were paid DRM or preview pages. No DRM bypass or pirated full text was downloaded. Real-book analysis, representative audio, current-SHA MuMu proxy playback, and human listening/signoff remain open gates.

## Latest Update: 2026-08-30 Android v0.9.2 version bump and root artifact layout

- Android app version was bumped in `app/build.gradle.kts` from `versionName 0.9.1` / `versionCode 88` to `versionName 0.9.2` / `versionCode 89` so an installed candidate can be distinguished from the prior same-version diagnostic package.
- A full ASCII build copy at `C:\CodexTemp\video-version-0.9.2-20260830-v2` passed `:app:testDebugUnitTest --rerun-tasks --no-daemon --offline` with 409 tests, 0 failures, 0 errors, 8 skipped, then passed `assembleDebug assembleRelease`.
- Current root handoff pair is exactly `老婆的小营地-debug-v0.9.2.apk` (49,316,597 bytes, SHA-256 `0B6FBB8D914FDF54819156C3BB5CF68B53D46C7BB52AA5FA70796BCE2D5D65C6`) and `老婆的小营地-v0.9.2.apk` (37,976,121 bytes, SHA-256 `A02354D4EF755AC9A8FA532C93A3BC020AE3DD8A3B29E4A0F2DCD75110E40D65`). `aapt2` reports `com.lovelyreader`, `0.9.2`, `89`, target SDK 35; v2 single-signature and zipalign checks pass.
- Superseded v0.9.1 root packages are archived under `artifacts/releases/legacy/20260830-v0.9.1-pre-0.9.2/`; the candidate record is `artifacts/candidates/version-0.9.2-20260830/README.md`. This is a local candidate only: no source push, tag or public GitHub Release was performed, and the home-Wi-Fi MA-14 player flow remains a physical-device gate.

## Latest Update: 2026-08-30 downloaded-cache full-chain and state-label fixes

- Correct user path is now现场 verified with legal synthetic fixture `桥接验收样书-精简全链路-v4`: App completed offline cache → Android import → public Sites v18 → company-computer local `local-qwen3-4b` whole-book analysis → locked all-custom voice bible → representative chapters → MuMu proxy download/play/background/resume.
- Remote task `77a2692a-135f-42bc-8e33-ccd38e04e17b` reached `preview_ready`, 100%, 3/3 ready. Three MuMu WAVs are `952,364` / `1,397,804` / `3,849,646` bytes and their SHA-256 values match worker and remote metadata; narrator is dedicated male custom voice, all roles use `voice_design_clone`, no preset speaker.
- Android status mapping now has `PREVIEW_READY` with “代表章节已生成，可离线播放”; the playback card distinguishes “正在播放”, “已暂停” and “本章已播放完”. New Debug candidate `0.9.2/89` SHA-256 `E26D9F5F907188DF065117AE3236F1F5E3B7B9D760BCD807FC850A1393BAB0E8`, `50,325,543` bytes, installed on MuMu.
- Final automation: worker 120/120, Android 418 tests with 8 skipped and 0 failures/errors, Sites 26/26 plus lint/build. Detailed evidence: `docs/tts/evidence/audiobook-e2e-20260830/downloaded-cache-rerun4-full-chain.md`; independent review summary: `docs/tts/team-audit/full-chain-acceptance-20260830.md`.
- Overall status remains incomplete: fixture is not the copyrighted target book, real long-book cold start/long-running recovery and human headphone/speaker/low-volume listening are open; no Android Release or formal product release.

## Latest Update: 2026-08-30 Round 2 reflection, independent review, and download-failure recovery

- 反思后发现并修复 Android 已存在任务自动同步的真实缺陷：手机章节下载失败原先可能被“已恢复后台进度”覆盖，失败原因也不保留。现在 `downloadErrorMessage` 与远端生成 `errorMessage` 分开，失败会持久化、清理 `.part` 半成品并显示“下载失败，可重试”；重复导入提示会汇总失败章节，刷新不会抹掉该状态。
- 当前候选为 `artifacts/candidates/download-failure-fix-20260830/老婆的小营地-debug-v0.9.2.apk`，versionName/versionCode `0.9.2/89`，SHA-256 `A783B08657B11E31460E1EC223D653E4215EA5CE309B4254DBE0A4ADDAC5FF10`，49,328,393 bytes；这是本地 Debug 候选，不是正式 Release。
- 隔离 ASCII 工作副本 Android 测试为 421 tests、0 failures、0 errors、8 skipped；worker 为 120/0/0；Sites 为 26/26，lint/build 通过。MuMu `127.0.0.1:16416` 已安装当前候选，并现场回放合法短夹具的正常导入、重复恢复、刷新、播放、切章、Home 后后台播放和返回进度恢复。
- 组织复核后的总状态仍是 `NOT_SIGNED_OFF`：PO、UX、QA、QC、架构和老听客都没有形成当前完整放行；普通用户仅对合法短夹具正常路径给出 `CONDITIONAL PASS`。复核记录为 `docs/tts/team-audit/20260830-full-chain/reflection-round2-20260830.md` 与 `user-journey-review-round2-20260830.md`。
- 仍未完成：合法目标版权正文、目标书整本冷启动分析和自主切章、长篇异步/重启/断电恢复、故障注入后的真实重试、播放器错误恢复、长时间播放，以及当前自定义旁白/角色声音的连续人耳验收。不能把短夹具写成目标书测试，也不能宣称产品完成或发布 Release。

## Latest Update: 2026-08-30 Round 3 failure recovery and independent signoff correction

- 继续反思后又修复了三个可操作性缺口：READY 章节现在显示可见“下载/重试下载”按钮；章节总数未返回时显示阶段化“章节数待返回”；Media3 播放错误保留在播放卡片中，并提供“重试播放”。远端生成失败只提供“重新查询”，不伪造未存在的服务端重试接口。
- 新 Debug 候选为 `artifacts/candidates/retry-ux-fix-20260830/老婆的小营地-debug-v0.9.2.apk`，version `0.9.2/89`，SHA-256 `3525283203A2DB871128831132023E151F020880405154FCC37A133F79CD0D48`，50,155,302 bytes；Android v2 签名校验通过，已安装 MuMu。
- 先红后绿策略测试后，隔离 ASCII 副本 Android 全量为 424 tests、0 failures、0 errors、8 skipped；worker 120/120；Sites 26/26、lint/build。MuMu 通过损坏音频触发下载失败、下载重试恢复、解码错误、播放重试恢复，以及正常代理播放/Home 后后台 MediaSession。
- 复核结论已修正并记录：PO、UX、QA、QC、架构、老听客均未对整体签署放行；普通用户仅对合法短夹具正常路径 `CONDITIONAL PASS`。总体仍 `NOT_SIGNED_OFF`。
- 仍未完成：合法目标版权正文、真实长篇冷启动和持续异步生成、真实网络断流/Range 恢复、重启/断电恢复、长时间播放、播放器跨进程恢复，以及自定义旁白和角色声音的连续人耳签收。当前候选仅供本地调试，不是正式发布。

## Latest Update: 2026-08-30 Round 4 restart auto-refresh verification

- 针对“重新进入 App 主动查询一次后台”的要求，使用当前候选 `0.9.2/89` 在 MuMu 执行 force-stop → 启动 → 进入听书页；进入后未点击手动刷新，等待自动流程结束后看到“后台状态已刷新”，返回顶部显示“听书服务已连接”。
- 之前短暂出现首次配置卡的画面属于自动刷新未完成的中间态；当前未确认状态丢失缺陷，但刷新进行中的等待提示仍有 UX 可见性问题。
- 证据：`docs/tts/evidence/audiobook-e2e-20260830/tts-review-20260830-auto-refresh-after-restart-bottom.png`、`tts-review-20260830-auto-refresh-after-restart-top2.png`。
- 本轮只证明 MuMu force-stop/重启和短夹具范围内的自动刷新，不证明断电/系统回收、长篇生成中途恢复、多任务并发、长时间后台播放或人工人耳音质；总体仍 `NOT_SIGNED_OFF`，不发布 Release。

## Latest Update: 2026-08-30 Round 5 visible recovery for failed books

- 独立普通用户回放发现真实 UX 缺口：书级 `FAILED` 显示“生成失败，可重新提交”，但当前客户端没有可见重提交按钮，也没有对应服务端 API。
- 已先写失败策略测试并确认测试能因缺少动作函数而失败，再实现最小修复：`FAILED`/`PARTIAL_FAILED` 详情显示“重新查询”按钮和能力边界说明，`FAILED` 标签改为“生成失败，请重新查询”。当前不伪造服务端生成重试。
- 新候选：`artifacts/candidates/failed-state-recovery-20260830/老婆的小营地-debug-v0.9.2.apk`，version `0.9.2/89`，SHA-256 `7795E06FD0459FEBFACEB593E3A2EE18559B6509B8F296D3B4D7B7921E02C136`，49,331,654 bytes；Android ASCII 隔离副本 `425 tests`、0 failures、0 errors、8 skipped。
- MuMu 现场确认失败详情按钮可见，点击后出现“正在查询后台章节进度…”，完成后显示“后台状态已刷新”；crash buffer 为空。截图在 `docs/tts/evidence/audiobook-e2e-20260830/tts-review-20260830-round5-failed-detail-with-query.png`、`tts-review-20260830-round5-after-query.png`、`tts-review-20260830-round5-after-query-complete.png`。
- 总体仍 `NOT_SIGNED_OFF`：服务端真正生成重试、真实长篇失败恢复、网络断流/断电、长时间播放和当前自定义声音的人耳签收仍未完成；当前候选仅本地 Debug，不是正式 Release。

## Latest Update: 2026-08-30 Round 6 automatic-refresh waiting state

- 复盘普通用户回放后确认：已有保存配置的用户进入听书页时，自动查询中间态不应显示“第一次使用”，否则会误以为配置丢失。
- 已先在 ASCII 隔离副本添加策略测试，再实现 `audiobookServiceCardTitle(...)`：保存配置且自动刷新中显示“正在查询后台”和“已保存的配置不会丢失”；完成后显示“听书服务已连接”。
- 当前候选：`artifacts/candidates/refresh-ux-visible-20260830/老婆的小营地-debug-v0.9.2.apk`，version `0.9.2/89`，SHA-256 `AAA5DD73CD6464FFEA42FB034407EAB7215FE56A54F2DC66F2F643DB4FFA454A`，49,332,138 bytes；ASCII 全量 `testDebugUnitTest --rerun-tasks` 为 `426 tests`、0 failures、0 errors、8 skipped。
- MuMu 无手动刷新现场证据：`docs/tts/evidence/audiobook-e2e-20260830/tts-review-20260830-round6-auto-refresh-early.png` 与 `docs/tts/evidence/audiobook-e2e-20260830/tts-review-20260830-round6-auto-refresh-complete.png`；候选包身份 `com.lovelyreader`、v2 签名 true、crash buffer 为空。
- 本轮只关闭等待态 UX 缺口；真实长篇、服务端生成重试、断流/断电、长时间后台播放、自定义声音人耳质量和六角色正式可读签署仍未完成，总体不发布 Release。

## Latest Update: 2026-08-30 Round 7 audiobook list discoverability

- 普通用户发现可播放书籍被 `UA探针`/`接口探针` 等等待任务压到列表下方，需要多次滚动；根因是客户端直接使用远端返回顺序渲染 `books`。
- 已先在 ASCII 隔离副本加入稳定排序策略测试，再实现 `audiobookBookDisplayOrder`：可播放优先、部分可听其次、失败/可查询任务再后、处理中任务最后，未知状态最低，同优先级保持原顺序。
- 当前候选：`artifacts/candidates/list-discoverability-20260830/老婆的小营地-debug-v0.9.2.apk`，version `0.9.2/89`，SHA-256 `A97AA8DA01E337771FD16A7DA380E0A776B4BEDBBC1D96C55D62B3882A883B53`，49,332,690 bytes；ASCII Android 全量 `427 tests`、0 failures、0 errors、8 skipped，`assembleDebug` 成功。
- MuMu 现场证据：force-stop → 启动 → 进入听书页 → 等待自动刷新 → 只上滑一次，已看到 `audiobook-e2e-20260829-v2` 排在等待任务之前；等待态/完成态截图在 `docs/tts/evidence/audiobook-e2e-20260830/tts-review-20260830-round7-auto-refresh-early.png`、`...round7-auto-refresh-complete-top.png`、`...round7-ready-order-after-one-swipe.png`。
- 本轮只关闭列表发现性 UX 缺口；章节数待返回无可靠 ETA、真实长篇、服务端生成重试、断流/断电、长时间后台播放、自定义声音人耳质量和六角色可读签署仍未完成，总体不发布 Release。

## Latest Update: 2026-08-31 Round 14 download consistency and independent review

- 继续反思后补上下载层保护：`ResumableFileDownloader` 对同一 `.part` 使用 `.part.lock` 文件锁；收到 `206` 时严格校验 `Content-Range` 起点；下载成功/失败的章节关键快照使用同步 SharedPreferences 提交。历史位置仍约每秒异步保存，最多约 1 秒位置损失是明确边界。
- 当前候选：`artifacts/candidates/download-lock-range-20260831/老婆的小营地-debug-v0.9.2.apk`，`com.lovelyreader`，`0.9.2/89`，49,457,778 bytes，SHA-256 `CB4BD2FE580AE4C8F5FC7787BE97C333D266617480B1F5D0D36778C230EDB592`；这是本地 Debug 候选，不是 Release。
- ASCII 隔离副本 Android 全量 `446 tests`、0 failures、0 errors、8 skipped；ASCII/Unicode `assembleDebug` 均成功；相关日志在 `docs/tts/evidence/audiobook-e2e-20260831/round14-*`。
- 新候选在 MuMu `127.0.0.1:16416` 使用主机代理 `10.0.2.2:7897` 重新验证启动、自动查询、一次上滑、详情、两章播放、Home 后后台 MediaSession、回到页面和手动刷新；后台位置证据为 `0 → 5964 ms`。
- 独立角色报告：PO/UX/QA/QC/老听客/普通用户均 `NOT_SIGNED_OFF`，架构 `CONDITIONAL`；汇总 `docs/tts/team-audit/reflection-round14-20260831.md`。总体仍 `NOT_SIGNED_OFF`。
- 仍未完成：合法真实《三生三世十里桃花》正文、整本分析与切章、当前候选绑定的全 custom voice manifest 和人耳签收、长篇服务端异步/CAS/分页、进程死亡/断电/低存储/多进程压力、正式 Release。不能把短 synthetic fixture 写成整本完成。

## Latest Update: 2026-08-31 Round 15 durable chapter commit and independent review

- Round 14 架构复核发现 `AudiobookRepository.commitChapterAudio()` 仍使用异步 `apply()`；已修正为 `persist(durable = true)`，与章节下载成功/失败快照统一使用同步 `commit()`。历史播放位置仍约每秒异步保存，最多约 1 秒位置损失是明确边界。
- 最新候选：`artifacts/candidates/durable-download-commit-20260831/老婆的小营地-debug-v0.9.2.apk`，`com.lovelyreader`，`0.9.2/89`，49,356,154 bytes，SHA-256 `FA7326E06AAAECD38159F766A80F93503535D8DEDB426880EEFCB626F8D2BF5A`；本地 Debug，不是正式 Release。
- ASCII 隔离副本 Android 全量 `446 tests`、0 failures、0 errors、8 skipped；ASCII/Unicode `assembleDebug` 均成功，三份 APK SHA 一致。
- 最新候选在 MuMu `127.0.0.1:16416` 使用代理 `10.0.2.2:7897` 完成自动查询、一次上滑、详情、两章播放、Home 后 MediaSession 位置增长、重新进入和手动刷新；证据为 `docs/tts/evidence/audiobook-e2e-20260831/round15-mumu-final-candidate/`。
- Round 15 独立复核：PO/UX/架构/普通用户 `CONDITIONAL`；QA/QC/老听客 `NOT_SIGNED_OFF`。普通用户已在 MuMu 独立走通最新候选的短 fixture 主路径；报告为 `docs/tts/team-audit/ordinary-user-review-20260831-final-candidate-r6.md`，汇总为 `docs/tts/team-audit/reflection-round15-20260831.md`。
- 总体仍 `NOT_SIGNED_OFF`。真实授权《三生三世十里桃花》全文、整本分析与切章、当前候选绑定的全 custom voice 连续人耳签收、长篇服务端异步/CAS/分页、进程死亡/断电/低存储/多进程压力和正式 Release 仍未完成。不要把短 synthetic fixture 写成整本完成，也不要把 Debug 候选称为已发布。

## Latest Update: 2026-08-31 Round 16 候选证据绑定补强与隔离复核

- 针对 Round 15 QA 指出的证据归属缺口，重新执行 ASCII Android 全量单测和 Unicode source Debug 构建，并从 MuMu 抽取已安装 `base.apk`。
- 候选、source Debug、ASCII Debug 和 MuMu 安装包均为 `49,356,154` bytes，SHA-256 `FA7326E06AAAECD38159F766A80F93503535D8DEDB426880EEFCB626F8D2BF5A`；测试为 `446 tests`、`0 failures`、`0 errors`、`8 skipped`，79 份 XML 和日志保存于 `docs/tts/evidence/audiobook-e2e-20260831/round16-final-audit/`。
- 本轮没有修改产品代码，只补强证据追溯性；随后让普通用户角色在同一 SHA 候选和 MuMu 上实际走通启动、自动查询、一次上滑、详情、两章播放、Home 后 MediaSession、返回和手动刷新。复核结果：PO/UX/QA/QC/老听客 `NOT_SIGNED_OFF`，架构/普通用户 `CONDITIONAL`；总体仍 `NOT_SIGNED_OFF`。现场报告为 `docs/tts/team-audit/ordinary-user-review-20260831-round16-live.md`。
- 真实授权目标书、整本分析切章、当前候选全 custom voice 连续人耳签收、长篇服务端 lease/CAS/分页、进程死亡/断电/低存储/多进程恢复和正式 Release 仍未完成。

## Latest Update: 2026-08-31 Round 17 Range 响应完整性校验与最新候选

- 上一轮架构复核指出 `206 Content-Range` 只校验起点，遗漏 `end/total`、区间长度、`Content-Length` 和实际 body 长度。已先红后绿补充 4 个测试，再补第 5 个超长 body partial 截回测试，同步修改 source 与 ASCII：统一解析完整 Range，异常区间或截断响应不进入 canonical atomic commit。
- 当前候选：`artifacts/candidates/full-range-validation-20260831/老婆的小营地-debug-v0.9.2.apk`，`com.lovelyreader` `0.9.2/89`，`49,358,679` bytes，SHA-256 `159905D08D5E8899FB55E6A93CA8D181F5D8531D6447DF3D59054AC987922B77`；source Debug、ASCII Debug 和 MuMu 已安装 `base.apk` 与此 SHA 一致。之后又补了超长 body 截回合法 partial 的回归，最终候选以该 SHA 为准。
- Android ASCII 全量为 `451 tests`、`0 failures`、`0 errors`、`8 skipped`；双目录 Debug 构建成功。MuMu `127.0.0.1:16416` 经 `10.0.2.2:7897` 代理完成自动查询、一次上滑、详情、三章播放、Home 后第三章 MediaSession 保持 `state=3/speed=1.0`、重进显示继续听进度和手动刷新查询中/完成态；证据在 `docs/tts/evidence/audiobook-e2e-20260831/round17-range-validation/`。
- 本轮仍没有完成整体交付：真实目标书整本、长期 custom voice 人耳签收、长篇服务端 lease/CAS/分页、断流/断电/进程死亡/低存储、多进程恢复和 PO/UX/QA/QC/架构/老听客/普通用户独立放行仍未完成。总体 `NOT_SIGNED_OFF`。

## Latest Update: 2026-08-31 Round 18 启动缓存自愈与 MuMu 回归

- 继续推进而非停止：针对架构复核提出的启动后缓存失配风险，先在 ASCII 隔离副本写 4 个失败测试，再新增 `reconcileAudiobookAudioCache()`，并让 `AudiobookRepository` 初始化时执行 `reconcileAudioCache()`。
- 规则：只保留 canonical WAV 存在、字节数等于当前章节 `audioBytes` 且为正、`.wav.meta` 完整 identity 与当前章节一致的缓存；只有 WAV、只有 marker 或 identity/大小失配都成对删除。只扫描当前持久化快照已知章节，不处理 orphan、`.part/.lock`。
- Android ASCII 全量为 `457 tests`、`0 failures`、`0 errors`、`8 skipped`；80 份 XML 在 `docs/tts/evidence/audiobook-e2e-20260831/round18-startup-cache-reconciliation/ascii-test-results/`；Unicode source 定向测试通过临时 ASCII 盘符链接，Unicode source `assembleDebug` 成功。
- 当前最终候选：`artifacts/candidates/startup-cache-reconciliation-20260831/老婆的小营地-debug-v0.9.2.apk`，`com.lovelyreader` `0.9.2/89`，`49,461,102` bytes，SHA-256 `3F43D5D38D3DB884D2C774A77ED4C4F349CF27F80028AF1DC24C3E9522563220`；ASCII/source/candidate/MuMu `base.apk` 一致。
- MuMu `127.0.0.1:16416` 经代理 `10.0.2.2:7897` 完成启动、自动查询、注入失配 marker 后 force-stop/重启自愈、列表、详情、三章播放、Home 后 MediaSession `state=3` 且位置 `0 → 5992 ms`、重进和手动刷新；crash buffer 为空。证据：`docs/tts/evidence/audiobook-e2e-20260831/round18-startup-cache-reconciliation/`。
- 总体仍未完成：真实目标书整本分析/切章/代表章节、全 custom voice 连续人耳质量、orphan/partial 清理、跨进程事务、长篇服务端稳定性、断电/低存储/多进程恢复、PO/UX/QA/QC/架构/老听客/普通用户最终放行、Release 仍未完成，保持 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog 或工时系统。

## Latest Update: 2026-08-31 Round 19 canonical 命名隔离与缓存事务锁

- 针对架构复核指出的命名混淆和跨实例文件竞争，先在 ASCII 副本加入失败测试，再把 revision-isolated 下载完成文件从 `*.wav` 改为 `*.wav.pending`；启动 canonical orphan 扫描只处理 `.wav/.wav.meta`，不会误删新的未提交 pending artifact。
- 新增 `withAudiobookCacheTransactionLock()`，使用共享 `.audiobook-cache.lock` OS FileChannel 锁；`AudiobookRepository` 的 canonical 提交、缓存命中、删书和启动 reconciliation 均在该锁内。`.part/.lock` 仍保留，因为它们可能承载续传工作。
- Android ASCII 全量为 `459 tests`、`0 failures`、`0 errors`、`8 skipped`；Worker `unittest` 为 `120/120 OK`；Sites `26/26`、lint、build 通过。ASCII/source/candidate/MuMu 四份 APK 均为 `49,371,188` bytes，SHA-256 `BD283697F1AB02E3BA8A28A2224F57EEC6B3E8C14B52CA59A6C4512347603F5E`。
- MuMu `127.0.0.1:16416`、代理 `10.0.2.2:7897` 完成最终候选 v3 的自动查询、列表/详情、三章播放、Home 后 MediaSession（`state=3`，位置 `0 → 5980 ms`）、重入、手动刷新；注入 legacy `.wav/.meta` 与 pending/partial/lock 后重启验证 legacy 被清理、pending/partial/lock 保留，crash buffer 为空。证据在 `docs/tts/evidence/audiobook-e2e-20260831/round19-cache-namespace-lock/`。
- 总体仍未完成：SharedPreferences 跨进程 CAS、音频/marker/快照崩溃事务、删除失败重试、`.part/.lock` 回收、低存储/断电/真实 kill、多进程压力、服务端长书 lease/CAS/pagination、真实目标书整本、连续 custom voice 人耳质量、独立角色最终放行、Release 仍未完成，保持 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog 或工时系统。

## Latest Update: 2026-08-31 Round 21 部分 detail 快照 fence 与新候选

- Round 20 架构复核发现：同 source、同章节总数但缺行的 detail 响应会覆盖本地章节集合，并可能继承旧 `chaptersSnapshotComplete=true`，启动时误删仍有用的 canonical WAV。已先在 ASCII 副本写失败测试，再同步 source/ASCII 新增 `mergeAudiobookChapterSnapshot()`。
- 现在部分 detail 同源时保留本地缺失章节行，但持久化 `chaptersSnapshotComplete=false`；换源不复用旧章节；`AudiobookApi.getBook()` 尊重显式 `chaptersComplete=false`，只有字段缺失时才用 count/ID fallback。完整 manifest 重新证明后才恢复 orphan cleanup 授权。
- Android ASCII 全量 `464 tests`、0 failures、0 errors、8 skipped；Unicode source `assembleDebug` 成功；Worker `120/120 OK`；Sites `27/27`、lint、build 成功。当前 candidate `artifacts/candidates/partial-detail-fence-20260831/老婆的小营地-debug-v0.9.2.apk`，`0.9.2/89`，`49,567,925` bytes，SHA-256 `A9EFDC1412B6645D1776711885A6CD0B80F955D005DFE9CF4A3A6B352072BF0D`。
- MuMu `127.0.0.1:16416`、代理 `10.0.2.2:7897` 安装新候选后重新完成自动查询、列表/详情、三章播放、Home 后 MediaSession `state=3` 位置增长、重入、手动刷新和缓存注入重启；完整书目录 legacy canonical 被清理，pending/partial/lock 保留，未确认 orphan 目录保留。证据：`docs/tts/evidence/audiobook-e2e-20260831/round21-partial-detail-fence/mumu-final-candidate-v5/`。
- 现状仍为 `NOT_SIGNED_OFF`：真实授权整本、整本分析/切章/固定声线、当前 SHA custom voice 连续人耳质量、长篇服务端 lease/CAS/pagination、失败重试、断电/低存储/真实 kill/多进程、Release 和独立角色最终放行仍未完成。本项目没有写入任何 worklog 或工时系统。

## Latest Update: 2026-08-31 Round 23 C3 严格 source identity 与完整 manifest reconcile

- 继续反思后发现 Round 21 的部分 detail fence 仍有列表路径缺口：无 source identity 的列表刷新可能把本地旧 `chaptersSnapshotComplete=true` 带回。已先写失败测试，再让 `mergeAudiobookBookLocalState()` 只有在 incoming/local source SHA 非空且相等、章节总数兼容时才继承完整标志。
- `reconcileAudioCache()` 现在在共享 `.audiobook-cache.lock` 内重新载入 snapshot，并同时要求书级 complete、非空 source SHA、当前章节数量正确、book 归属正确、chapter ID/ordinal 唯一且 ordinal 连续覆盖 `1..chaptersTotal`。
- Android ASCII 全量为 `469 tests`、`0 failures`、`0 errors`、`8 skipped`；Unicode source/ASCII Debug 构建成功；Worker `120/120 OK`；Sites `27/27`、lint、build 成功。
- 当前候选：`artifacts/candidates/complete-manifest-fence-20260831/老婆的小营地-debug-v0.9.2.apk`，`com.lovelyreader` `0.9.2/89`，`49,470,911` bytes，SHA-256 `4CEECA4861620C9A214ACAD2683EDC861DE9D684D7B6E01AFD33B8C4839D0DA7`；source/ASCII/candidate/MuMu installed `base.apk` 一致。
- MuMu `127.0.0.1:16416` 经主机代理 `10.0.2.2:7897` 完成查询、列表、详情、三章播放、Home 后台播放、重入、手动刷新；受控缓存注入证明完整书目录 legacy canonical pair 被清理，pending/partial/lock 与 `orphan-round23` 保留，`cache_assert=0`。证据见 `docs/tts/evidence/audiobook-e2e-20260831/round23-complete-manifest-fence/`。
- 七个隔离角色已基于同一 C3/v7 独立复核：PO/QC/老听客 `NOT_SIGNED_OFF`；UX/QA/架构/普通用户 `CONDITIONAL`。总体是“短样本技术检查点通过、整体产品 NOT_SIGNED_OFF”。
- 仍未完成：合法真实《三生三世十里桃花》整本导入/整本分析/切章/代表章节选择、当前 C3 custom voice 连续人耳签收、后端 manifest revision/稳定分页/lease-CAS、断流/失败重试/断电/低存储/真实 kill/双进程/长篇压力、正式 Release。本项目没有写入任何 worklog 或工时系统。

## Latest Update: 2026-08-31 Round 24 manifest revision / pagination

- 已在 Sites 增加 `reader_books.manifest_revision` 和 task revision：分析完成在租约保护下递增并固定 render task，render complete 拒绝 task/book revision drift；detail/chapters API 增加 bounded ordinal cursor。
- Android source 与 ASCII 同步逐页获取章节，校验 book/source/revision，章节缓存 identity 纳入 manifest revision；正 revision 一侧缺失或变化时不继承旧完整清单/旧下载结果。新增“revision 消失不得保留 cleanup authority”回归。
- Sites `30/30`、lint/build；Worker `120/120`；Android full `472/0/0/8`、双 Debug build。当前 candidate `artifacts/candidates/manifest-revision-pagination-20260831/老婆的小营地-debug-v0.9.2.apk`，`49,487,925` bytes，SHA-256 `AFADA75C4F8734FF8CC9A11D6DA9244C5B4F2027DE496818353E1E5D26A8EC7B`。
- Sites version 19 已部署到公开 URL；MuMu `127.0.0.1:16416` / `10.0.2.2:7897` 通过新包查询、列表、详情、两章播放、Home 后台、重入、刷新；线上 `limit=1` fixture 分页验证 ordinal 1 → cursor 1 → ordinal 2 → null。
- 工程检查点通过但总体仍 `NOT_SIGNED_OFF`：live fixture 当前为 legacy `manifestRevision=0`，正 revision 101+ 章真实 worker 现场、目标书整本、custom voice 连续人耳、崩溃/断电/低存储/双进程恢复、独立角色最终放行和正式 Android Release 未完成。本项目没有写入任何 worklog 或工时系统。

## Latest Update: 2026-08-31 Round25 — 正 revision 101 章整书 checkpoint 与当前候选绑定

- 使用项目自有合法 synthetic `positive-revision-101-book-v2.txt` 完成 101 章整书分析，源 SHA-256 `cef27a1dad29af3df1f815080971ee9b2fe3e9701ff273f8d180881ba093c0c9`；服务端状态 `preview_ready/preview_completed/progress=100`，`manifestRevision=1`，`chaptersReady=5/101`，代表章节为 `1,2,3,4,101`。
- `/chapters?limit=25&manifestRevision=1` 五页收集 `1..25/26..50/51..75/76..100/101`，101 条无重复且 ordinal 连续；第 101 章为 selected/ready。五个 WAV 均 24 kHz mono PCM16、`all_custom`、`voice_design_clone`，自动 QC 通过；整体仍 `technical_ready_subjective_pending`。
- Android 修复 JSON null 解码：`JSONObject.NULL` 和历史字符串 `"null"` 不再进入错误字段；真实错误文本仍保留。`AudiobookSnapshotCodecTest` 覆盖三类输入。
- 当前候选为 `artifacts/candidates/round25-final-20260831-r2/老婆的小营地-debug-v0.9.2-89.apk`，49,376,312 bytes，SHA-256 `1C56FF04EA1D118079B412E7AF5B7BD62517B8D0FB1F85DB10E92E3C80D7EA98`。MuMu `127.0.0.1:16417` installed/pulled `base.apk` 与候选 SHA/bytes 一致；代理 `10.0.2.2:7897`。
- 当前候选现场完成在线刷新、列表/详情、前台播放、Home 后后台 MediaSession、重入、代理不可用时缓存播放、恢复代理后的状态收敛；UiTestAutomationBridge 返回 null root，部分现场操作用了已记录 screenshot-coordinate fallback。
- Worker 使用 `.venv` 显式测试集合为 `129/129 OK`，包含同进程、独立 PID 竞争及持锁进程强制终止后的同工作区 OS 单实例锁接管；Android source 经同一源码的 ASCII Junction 全量 `481/0/0/8`，ASCII mirror 同值；Sites `34/34`、lint exit 0。早先 system-Python `79/8 errors` 仅为错误入口诊断。
- 八个隔离角色的原始报告已保存，整合见 `docs/tts/team-audit/reflection-round25-20260831.md`；候选绑定问题已由后续证据关闭，但人工听感、真实目标小说、96 章音频、故障/长时压力、Release 和整体签收仍未完成。总体保持 `NOT_SIGNED_OFF`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-10-02 Round37：排行榜公开评分切片扩展与中国年份真实分页（v0.9.24/111）

- 用户实测指出中国筛选下没有 2025/2024/2026，之前榜单只有少量高分样本；本轮将排行榜门槛从公开评分票数≥1000 降到≥100，重新生成 `catalogVersion=2026-10-02.4`，共 200,497 条。
- 当前静态统计：CN 2024=112、2025=102、2026=31。地区筛选把中国/韩国/泰国等常用项置前，年份 2026/2025/2024 直接可见，结果显示条件总数和当前页范围。
- Android 运行时继续使用 `catalog-index.json` + `items.ndjson` 流式读取；MuMu `emulator-5554` Release 实测中国+2025 从 1–50 加载到 51–100、101–102，筛选状态保持，无 OOM/应用崩溃。
- v0.9.24+111 Debug/Release 已构建、签名、zipalign；根目录严格保留当前版本化 pair，v0.9.23 移入 `artifacts/releases/legacy/20261002-v0.9.23-before-ranking-v0.9.24/`。候选证据见 `artifacts/candidates/20261002-ranking-full-catalog-v0.9.24-final/`。
- Python 13/13；Android 574/0/0/8 skipped。此次“全量”只指 IMDb 公共评分≥100、1950-current、支持 title type 的离线切片；TVmaze/Wikidata/中文维基仅做增强，不等于实时全互联网多站点综合。长尾海报/完整剧情、golden 像素和公共 Release 仍未签收。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-10-02 Round38：近期中国中文身份增强与 v0.9.25/112 复测

- 独立视觉复核指出 v0.9.24 的近期中国结果仍可能漏出英文标题、卡片使用占位海报；本轮没有把首屏高分样本再次当全量，而是在 200,497 条完整 IMDb `numVotes>=100` 切片上优先增强当前年起两年的 CN，再处理 KR/TH 与全球长尾。
- `catalogVersion=2026-10-02.5`、`identityMapVersion=...-v6`；`tt34071601` 精确覆盖为“路过我年少时光的蓝色”，附中文简介与 TVmaze 海报。资产测试新增中国 2024/2025/2026 最低行数 112/102/31 和逐条中文显示名门禁。
- MuMu `emulator-5554`（720×1280）新鲜复测：CN 2026=31、2025=102、2024=112；2025 由 1–50 加载到 1–100、101–102；分页后重新展开筛选仍显示“中国，已选中”“年份2025，已选中”；进程保持存活。
- v0.9.25/112 Debug/Release 已构建、签名、zipalign，根目录只保留当前版本化 pair；候选证据和 SHA 在 `artifacts/candidates/20261002-ranking-full-catalog-v0.9.25-final/`，v0.9.24 pair 在 `artifacts/releases/legacy/20261002-v0.9.24-before-ranking-v0.9.25/`。
- Python 14/14；Android 574/0/0/8 skipped。当前仍是本地候选，未 push/tag/创建公开 Release；多站点授权实时综合、145,591 条无可验证制作国家、长尾海报/完整剧情和 golden/TalkBack/200% 字体门禁仍未签收。
- 独立 Visual/UX-QC 复测结论为 CONDITIONAL PASS：CN 2026/2025/2024 分页末页可达 31、101–102、111–112，筛选状态与 Crash buffer 通过；多数卡片仍是“封面占位”，所以真实图片全量和高保真最终验收继续 OPEN。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-10-02 Round：v0.9.23+110 全量目录与流式读取候选

- 用户指出中国年份筛选为空/数据过少。根因是前一轮把有限快照和发行市场口径误当成完整制作国家排行；本轮改为 IMDb 公开评分票数≥1000 的完整 1950-current 范围，共 63,681 条（电影 50,186、剧集 11,586、综艺 1,909）。制作国家优先 Wikidata P495，IMDb AKA 市场单独放入 `releaseMarkets`。
- 目录验证：CN 制作国家 2024=33、2025=37、2026=9；Python 13/13，Android JVM 573/0/0/8 skipped。中国筛选与三年结果已在 MuMu `emulator-5554` Release 实测。
- 运行时修复：保留 `catalog.json` 审计资产，同时生成 `catalog-index.json` 与 `items.ndjson`；`BundledRankingRepository` 流式扫描/二次读取当前页，解决 43 MB JSON 在设备上一次性解析导致的 OOM。该修复不触碰 audiobook、播放器、更新器。
- v0.9.23/110 APK 已构建、签名和 zipalign 验证；根目录仅保留版本化 Debug/Release pair。候选证据见 `artifacts/candidates/20261002-ranking-full-catalog-v0.9.23-final/`；这是本地候选，未 push/tag/创建公开 Release。
- 诚实限制：长尾仍可能缺少合法海报和完整剧情正文，当前不是 TMDB/Trakt/豆瓣实时授权多源排行，视觉/元数据门禁为 CONDITIONAL，不得描述为所有条目都已补齐。

## 2026-10-01 RANKING-DATA-001 中文优先与地区覆盖最终候选

- 排行榜当前本地候选为 `0.9.21/108`；`catalogVersion=2026-10-01.2`，`methodologyVersion=weighted-stratified-v3`，`identityMapVersion=imdb-tvmaze-wikipedia-zh-v3`。快照 188 条（电影 60、剧集 64、综艺 64），展示标题英文字母计数 0，分类字段均为中文。
- 覆盖统计：剧集 CN 8/KR 10/TH 9、2016+ 30；综艺 CN 6/KR 3/TH 2、2016+ 11；电影 CN 11/KR 9/TH 5、2016+ 33；188/188 条均有国家/地区字段。近期区域种子和区域电影种子已纳入，原始英文身份仅保留为搜索/审计字段。
- 客户端已去除可见英文原名和 `TVmaze/IMDb` 标签，改为中文来源/方法文案；默认榜单请求 50 条。追剧片源、播放器、更新器和有声模块没有读取/写入排行榜状态。
- 验证：Python `6/6`；Gradle `569 tests / 0 failures / 0 errors / 8 skipped`，Debug/Release build、v2 签名、zipalign 通过；MuMu `127.0.0.1:16384` 安装 Release `0.9.21+108`，`24–39-final` 与重装后的 `41–46-country-complete` 筛选证据留存，`40-final-crash-buffer.txt` 为空。
- 根目录只有版本化 Debug/Release pair，哈希见 `docs/artifact-layout-20260827.md` 与候选 `SHA256SUMS.txt`。当前状态是 source + local candidate，未 push/tag/公开发布；实时多源 feed、TMDB/Trakt、排行榜 golden 像素基线仍是明确限制。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-10-01 RANKING-DATA-001 中文优先与覆盖修复

- 用户反馈排行榜大量英文、2015 年后内容缺失、没有大陆/韩国/泰国。根因是生成器只抓 TVmaze 前 5 页并按全球加权分直接截断，电影只取 IMDb all-time 高分；不是片源搜索站点故障。
- `source/ranking-service/build_catalog.py` 现用 `weighted-stratified-v2`：TVmaze 15 页 + 区域种子，按 CN/KR/TH/JP/GB/US 与 2016+、2010–2015 分层后填充；电影先保留 2016+ 新作。加入中文 AKA、中文维基和稳定中文标题映射；类型全部中文；英文简介转为中文缺省文案，英文原名保留在 `originalTitle/aliases`。
- 新内置 `catalogVersion=2026-10-01.1`：188 条（电影 60、剧集 64、综艺 64）；剧集 CN 8、KR 8、TH 7，2016+ 剧集 26；英文简介 0；展示标题仅 `1917` 无汉字。
- Android 版本升至 `0.9.21/108`（本地候选，不是公开发布），排行榜技术元数据显示改为“分层综合参考 v2 · 中文身份映射”。Gradle `568/0/0/8`、Python 生成器 `4/4`；MuMu Release 实测中国“陈情令”、韩国“请回答1988”、泰国“两小无拆”，crash buffer 为空。
- 根目录只保留 `老婆的小营地-debug-v0.9.21.apk` / `老婆的小营地-v0.9.21.apk`；旧 `v0.9.20` pair 归档到 `artifacts/releases/legacy/2026-10-01-v0.9.20-ranking-data/`。候选清单见 `artifacts/candidates/20261001-ranking-data-v0.9.21/candidate-manifest.md`。
- 本轮没有 source push、tag、GitHub Release 或 updater manifest；实时行业 feed、电影国家字段完整性和排行榜 golden pixel baseline 仍是公开发布门禁。

## 2026-09-07 Round57-59：整书代表章节生成的 CPU/内存档位与恢复现场

- 当前书 `微微一笑很倾城` 仍为整书分析、代表章节生成范围 `[1,11,13,15,49]`；第 1、11、13、15 章 ready，第49章是唯一未 ready 代表章节。
- Round58 使用 full/float32/batch=4 生成第49章，完成到 48/327 后因内存安全检查进入 throttle；随后在第24片段后的安全边界退出，没有强杀正在执行的模型调用。服务端回收 task 为 retryable，不能把 `rendering` 误记为完成。
- 为缩短剩余时间且保留白天 08:30-18:30 的半资源约束，launcher 的夜间 full batch 已从 4 调整为 2；白天 throttle 仍为 6 线程、batch=1、约50% CPU亲和性、below-normal 和内存门禁。
- Round59 于 2026-09-07 23:25:24 重新领取同一 task；23:28:23 首批返回 2/327，服务端第49章约 `rendering/6`、代表章节 `4/5`，系统可用内存约 5.94GB，worker 工作集约 8.48GB。当前仍在异步生成，整体 `NOT_SIGNED_OFF`。
- 变更证据与未完成门禁见 `docs/tts/task-change-report-20260903.md` Round59、`docs/tts/test-matrix.md` Round59、`docs/tts/evidence/audiobook-e2e-20260904/round59-full-batch2-live.txt`；未写入 worklog/timesheet。

## 2026-09-07 Round56：章节重试状态归一化并重新领取第49章

- 发现第49章受控停机后出现 task=queued、chapter=failed_retryable 的状态错位；原章节 retry route 将 queued 直接 replay，前台“重新查询”也没有真正重试动作，worker 空队列轮询缺少可观测日志。这解释了用户等待很久仍看不到成功返回。
- Sites version 45 加入前台“重试本章”；version 46 修复 queued + retryable chapter repair：清除旧 next_attempt_at，保留已验证分析输入，恢复 task=queued/chapter=pending。站点 62/62、build、公开 deployment succeeded；source commit 28f8a8aecc8211edbcc7b67ff20ac594ec835cf3。
- MuMu 现有会话调用真实第49章 retry 返回 replay=false/retryScheduled=true；worker round56-retry-v46-20260907 日志显示 3.0 秒领取 render task，full/float32 TTS 模型加载成功，首批 batch=4。当前 chapter=rendering，还未 ready。
- 仍未完成：第49章 ready、5/5、手机端同步下载/播放/后台/历史进度、跨时段 full/throttle live 切换、人耳 QC、团队独立复核。Android v0.9.19/106 未因本轮站点/worker 修复而重打包；整体继续 NOT_SIGNED_OFF。

## 2026-09-07 Round57/58：CPU dtype 与内存保留余量校准

- 现场确认当前公司电脑为 Intel i7-12700，PyTorch CPU capability 为 AVX2。Round56 throttle 使用 bfloat16 时，63 字单元耗时约 14 分 55 秒，虽未崩溃但不具备可用生产性；按安全检查点退出，没有强杀。
- 运行 `ops/benchmark_tts_capacity.py` 做 float32、6线程、batch1 基准：33 字文本 load 8.26 秒、render 58.14 秒、音频 7.36 秒。启动脚本保持白天 6线程、batch1、约50% CPU affinity、below-normal 和 sleep，仅将 throttle dtype 从 bfloat16 改为 float32。
- Round57 真实路径验证 float32 throttle 单元约 1分41秒，但旧 TTS 内存保留 1.5GB 在约 4.34GB 可用内存时触发批次间等待/重载；安全停机后将保留余量调为 1.0GB，同时保留 3GB free-memory floor，不等于取消内存门禁。
- Round58 `round58-float32-reserve1-v46-20260907` 于 22:16:57 重新领取第49章；full/float32 连续完成 4、8、12、16、20、24、28、32、36、40、44/327 单元，未再出现每批立即等待/重载，22:51 服务端为 `ch49 rendering/16`、代表 ready `4/5`，主机可用内存约5.18GB。该轮仍在生成中，尚未达到第49章 ready。
- 新增证据：`docs/tts/evidence/audiobook-e2e-20260904/round57-float32-profile-live.txt`、`docs/tts/team-audit/round56-isolated-review-20260907.md`。独立角色最近可读取结论仍为 BLOCKED；当前整体继续 `NOT_SIGNED_OFF`，待 5/5、手机第49章完整播放链路、听感 QC、故障恢复及正式发布门禁。

## 2026-09-06 Round53 后续：安全停机回队语义修复与目标书续跑

- 07:00–07:03 的真实 stop-marker 现场确认 worker 会等待当前模型调用返回，在章节片段生成后的安全边界退出，未留下 `.part`/`.tmp`；但旧 Site fail route 把 `WORKER_SHUTDOWN_REQUESTED` 在 attempt 3/3 错判为 `failed_permanent`，缺陷编号 `BUG-20260906-SHUTDOWN-EXHAUSTS-RETRY`。
- Site 已推送 commit `f9dfc2a0fd07cebff5dbb379d4406a4f83a3d07e`，公开 version 44 deployment succeeded；受控停机码现在与资源窗口/内存受限码一样回队且不消耗 retry budget，普通失败仍保留永久失败判定。Site version 44 不是 Android release。
- 通过 MuMu 客户端重试路径把第49章恢复为 chapter `pending/0`、task `queued/attempt0`，stop marker 已可恢复地移动为 consumed 文件；`round53-resume-v44` 已重新领取并在 07:19 完成第1批，当前持续生成。
- `07:21:14` 快照：目标书 `generating/81`，第1/11/13/15章 ready，第49章 `rendering/6`，代表 ready `4/5`。当前整体仍 `NOT_SIGNED_OFF`；待第49章、资源降档现场、修复后 stop live 回队、人耳/编辑 QC、实体手机、故障恢复、隔离角色可读签收和 Android public release。
- 07:34 续跑快照：本地第49章 TTS `20/327`，服务端 `generating/82`、`rendering/10`，`round53-resume-v44` 推理子进程仍为 `BelowNormal`。代表仍 `4/5`，不把进度前进当作完成。

## 2026-09-07 Round54：异常退出后的租约恢复与工作时段 live 降档

- `round53-resume-v44` 在 2026-09-06 09:45:34 第49章第12批开始后无 traceback/安全停机日志地退出，父子进程消失；系统事件未提供明确 OOM/崩溃根因，登记为 `BUG-20260907-WORKER-EXITS-WITHOUT-ERROR`，不能称 worker 长时稳定。
- Site v44 租约回收将状态恢复为 book `generating/queued/80`、ch49 `pending/0/attempt2`；第1/11/13/15章仍 ready，代表 `4/5`。
- 2026-09-07 17:41 已启动 `round54-resume-v44-20260907`。真实日志确认工作时段 throttle：6线程、batch1、bfloat16、约50% CPU亲和性；重新领取第49章并开始第1批。
- 当前仍需第49章、手机端同步/播放、异常退出稳定性、主观/编辑听感、实体机、故障恢复、隔离角色签收和 Android public release；整体 `NOT_SIGNED_OFF`。
- 17:46 MuMu 重连并刷新后，APP 显示“正在生成音频”、`81% · 4/5 个代表章节可听（全书49章）`、`最近同步：刚刚`、`后台状态已刷新`、缓存 `4/4`；第15章历史进度54%。第49章仍未 ready。

## 2026-09-07 Round55：跨资源时段 TTS batch 动态刷新

- 现场发现 `round54-resume-v44-20260907` 在 17:41 以 throttle batch=1 开始第49章；18:43 已切换 full、12线程、float32、batch4，但 renderer 因章节开始时静态快照 batch，仍逐片段推理，19:26 仅完成 `12/327`。登记 `BUG-20260907-BATCH-PROFILE-SNAPSHOT`。
- 先新增动态 batch provider 测试并在旧代码下 RED；实现后 `render_annotated_chapter()` 在每个 batch checkpoint 后重新读取 batch 预算，worker 传入 `self.tts_batch_size`。当前模型调用不被中断，下一批才切换，直接调用者仍可使用静态 `batch_size`。
- Worker 全量 `217/217`、`py_compile`、launcher parser 通过。旧进程已创建 stop marker，尚在第13批模型调用中；需安全回队并用修复后的新 worker取得 full/throttle live、5/5、手机第49章闭环。整体仍 `NOT_SIGNED_OFF`，未构建 Android。

## 2026-09-06 Round53 续跑快照：worker 持续推进与安全停机回归

- 新 worker 父 PID `62120`、推理子 PID `46652` 仍在运行；第15章已从第6批 `24/98` 推进到 `98/98` 并于 `06:00:48` 被 Sites 确认为 `ready/100`，第49章随后进入 `rendering/5%`。代表 ready 已从 `3/5` 升为 `4/5`。
- Worker 安全停机 TDD `2/2`，全量 `216/0/0`，`py_compile` 和 launcher 解析已通过；live stop 文件验证仍需在当前章安全完成后用新代码实例执行。
- 第15章清单为 `all_custom`，包含 narrator、微微、雷神妮妮、愚公四个角色；64次角色切换最小间隔1秒，技术 review gate 仍是 `technical_ready_subjective_pending`。MuMu 刷新后已缓存4/4并播放第15章，Home 后 MediaSession 保持 `state=3/error=null`，重入显示 `继续听到54%`。当前仍不得签收5/5、49/49、08:30 throttle/bfloat16现场、故障恢复、主观/编辑听感、隔离团队最终可读取回执或 Android public release。

## 2026-09-06 Round52：章节行级提示修正与 Android v0.9.19/106 候选

- 修复 Android 章节行辅助文案：`RENDERING` 显示“生成中”，`WAITING_RESOURCE_WINDOW` 显示“等待空闲”，不再把非 ready 状态统一说成“等待”。测试先红后绿。
- 全量 `./gradlew test` 发现并修复 Release 测试 source-set 问题：debug-only fixture 测试从 `src/test` 移至 `src/debugTest`。当前 Debug/Release 各 `503/0/0/8 skipped`，合计 `1006/0/0/16 skipped`。
- Android 本地候选已按规则递增至 `v0.9.19/106`；Debug SHA `97ADF77BA62F0BFA766D167A95983B3BD6B7753FAE9A59105128E393A2C201A1`，Release SHA `8CEE8A564675B909E1280660CB4E068BD547B6C03B2A4184F5E3B6FCAF64CAB1`；旧 v0.9.18 pair 归档到 `artifacts/releases/legacy/2026-09-06-v0.9.18-before-rendering-hint/`。
- MuMu `127.0.0.1:16384` 安装并重入目标书成功；后台事实为第1/11/13 ready、第15 `waiting_resource_window/18`（内存压力）、第49 `rendering/8`，APP 显示 `3/5`，第13章可播放，第15章“等待空闲”。详见 `docs/tts/evidence/audiobook-e2e-20260904/round52-rendering-hint-live.txt`。
- 后台 worker 仍是旧逻辑运行实例，当前 child `53352` 正在渲染第49章，不得把新 memory-safe 代码说成 live；第49章到达安全边界后才可受控重启并验证 throttle/bfloat16/60秒退避，再继续第15章。
- 当前交付仍 `NOT_SIGNED_OFF`：代表目标未到5/5，整书49/49、人耳/编辑 QC、实体手机、长时/故障恢复、可读取隔离角色复核和 Android public release 仍 OPEN。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-06 Round50：内存压力后的重复重载修复进行中

- 现场发现旧 clean-start Worker 在第11章生成时于安全边界触发 `RESOURCE_MEMORY_PRESSURE`，约3秒后仍按夜间 float32/batch4 重复 claim/重载；`01:11:54` 与 `01:17:37` 两次复现，解释了用户长时间看不到 ready 的真实原因之一。
- Worker 已增加 memory-safe mode：内存暂停后后续重试强制 throttle/bfloat16/低 batch/半 CPU 亲和性；重试退避至少60秒；释放 TTS runtime 同时清空 custom voice prompt cache。定向用例先红后绿。
- 当前旧 PID `61108/53352` 尚未在新代码下重启；必须等安全边界后精确停止本项目父子进程，再启动新 worker 取得现场 `throttle/bfloat16` 日志。不得把代码测试或旧进程继续运行当成新逻辑 live 证明。
- 第1章已在 MuMu 下载/播放；第11章随后已在 MuMu 下载/播放，剩余代表章、5/5、49/49、人工/编辑 QC、实体手机、跨窗/故障恢复、可审计八角色复核和 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。
- 详见 `docs/tts/evidence/audiobook-e2e-20260904/round50-memory-safe-retry.txt`、`docs/tts/bug-experience.md`、`docs/tts/decision-log.md`、`docs/tts/test-matrix.md`。

### Round50 续跑现场：第11章 ready、同步、下载与播放（2026-09-06）

- 同一 clean-start 目标书 `049f5e4f-15a3-446e-9564-73f274bb8152` 的第11章已真实回传，Sites live D1 为 `chapters_ready=2/49`、failed `0`；第13章仍在 `processing`，第15章 queued。
- MuMu `127.0.0.1:16384` 刷新后显示 `41% · 2/5 个代表章节可听（全书 49 章）`、`最近同步：刚刚`、`手机已缓存 2/2 章，可离线播放`，第11章显示“已下载，可播放”；点击后显示“正在播放”，MediaSession `state=3`、`error=null`。
- 第11章 MP3 SHA-256 `80B50DED2D8A935E06542E656957B215455401BAD2FF43765B7E7048EB3FFFBD`；原始证据 `docs/tts/evidence/audiobook-e2e-20260904/round50-second-ready-and-playback.txt`。
- Round50 新 memory-safe 重试逻辑已通过 `214/214`，但当前正在运行的 worker 是旧代码启动实例；必须等安全边界后重启并取得真实 throttle/bfloat16/60秒退避日志。5/5、49/49、人耳/编辑 QC、实体手机、长时/故障恢复、可审计团队复核与 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。

## 2026-09-06 Round51：章节状态实时回显与 v0.9.18/105 本地候选

- 真实现场发现服务端/设备落盘第13章已是 `processing/rendering`，但 Android 详情行仍显示“等待”。根因是章节列表经非响应式 repository 读取，书籍列表等值时章节变化没有触发 Compose 重组；不是后台没有推进。
- Android 新增 `AudiobookUiSnapshot` 与单调 revision，`publishBooks()` 同时发布书籍/章节快照，详情页按 `book.id + revision` 重读章节。TDD 先红后绿；ASCII Junction 全量 `505/0/0/8 skipped`。证据：`docs/tts/evidence/audiobook-e2e-20260904/round51-chapter-progress-live.txt`。
- 版本已递增为 `0.9.18/105` 并构建、签名/对齐核验、安装到 MuMu `127.0.0.1:16384`；真实详情先显示 `生成进度 51% · 2/5` 和第13章 `音频生成中 55%`，随后刷新显示 `61% · 3/5`、手机缓存 `3/3`，第13章已下载可播放并能前台播放。根目录只保留 v0.9.18 Debug/Release pair，候选校验见 `artifacts/candidates/2026-09-06-chapter-status-sync-v0.9.18/SHA256SUMS.txt`。
- 本轮关闭了客户端状态传播缺陷，并取得第13章回传/下载/播放证据；第15章已开始生成，第49章待后续。5/5、49/49、人耳/编辑 QC、实体手机、长时/故障恢复、Round50 新 worker 现场重启、可审计团队复核和 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。

## 2026-09-05 Round46：最近同步可见性与 v0.9.17 本地候选

- Android 新增领域 helper `audiobookLastSyncedLabel(book, nowMillis)`，优先用服务端 `updatedAt`、回退 `createdAt`，书架卡片和详情显示相对时间；无效/缺失时间不显示，标签不伪造进度或 ETA。页面用已有 60 秒时钟重算，手动刷新/重入仍查询真实后台状态。
- v0.9.17/versionCode 104 已构建并安装 MuMu `127.0.0.1:16384`；现场首页/详情显示远端后台书、代表 `5/5（全书49章）`、`最近同步：1 小时前`，UI XML 未命中 token/令牌、代理、模型、线程等内部配置。根目录只保留 v0.9.17 Debug/Release pair，候选 checksum 见 `artifacts/candidates/2026-09-05-last-sync-ux-v0.9.17/SHA256SUMS.txt`。
- Android `503/0/0/8 skipped`、Worker `207/207 OK`、py_compile、pip check、构建、aapt2、Release v2 单 signer 和 zipalign 通过。五个代表章 quality endpoint 技术门通过，但主观听感/编辑门仍 pending。
- 本轮 8 角色独立线程最新均 completed/idle、`items=[]`、无可读取正文；按 `docs/tts/team-audit/reflection-round46-20260905.md` 记为不可审计 `UNVERIFIED`，不能推断角色通过。
- 当前范围仍为 49 章中的代表目标 `1/11/13/15/49`，5/5 ready，不是整本 49/49；clean-start、实体手机/锁屏耳机、长时/跨资源窗口/进程/低存储/断电恢复、人耳/编辑 QC 和 Android public release 继续 OPEN，整体 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-05 Round46 clean-start 补充：真实找书到导入链路

- 对 MuMu `127.0.0.1:16384` 执行 `pm clear com.lovelyreader` 后，首次打开 APP 无令牌/代理/模型/线程输入；进入找书搜索《微微一笑很倾城》，在 APP 内下载完成，再回听书页识别 `完整正文已在本机` 并点击导入。
- 导入后远端卡片真实显示 `后台有声书`、`正在排队处理，章节数待返回`、`最近同步：刚刚`；UI 扫描 token/令牌、代理、模型、线程均为 0。原始证据：`docs/tts/evidence/audiobook-e2e-20260904/round46-clean-start-flow.txt` 及同目录 XML/PNG。
- 清除数据会生成新的匿名 client identity，旧测试书不会在新 client 的首次页面自动出现；这是隔离事实。为避免污染已完成的代表测试书，worker 仍绑定旧目标，所以本补充只证明新用户入口/导入/排队展示，未证明新任务音频生成。
- 完整 clean-start gate（新任务章节返回、生成、同步、播放和故障恢复）继续 OPEN；当前代表测试书仍 `5/5` 而非 `49/49`，总体 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-05 Round47：文本分析模型加载内存门禁修复与现场续跑

- clean-start 新任务书籍 ID 为 `049f5e4f-15a3-446e-9564-73f274bb8152`。旧 float32 文本分析 worker 在 `23:05:31` 领取分析任务后，子进程工作集约 `13.2 GB`，主机总内存约 `31.72 GB`、可用内存降至约 `0.59 GB`；确认是本项目父/子进程后安全停止，未停止其他项目或用户进程。
- 根因是原有门禁只覆盖 claim 前和 TTS 模型 loader 前，`_analyze()` 的文本分析模型 loader 没有独立内存 reserve。已在 worker `resource_window.py`/`audiobook_worker.py` 增加 `analysis_model_load_pressure()` 与 loader 前 `WorkerMemoryPause`，并将 launcher 的 4B 文本模型设为 bfloat16、额外 reserve 10 GB。
- TDD 先红后绿，定向 `2/2`、Worker `209/209`、非 venv `py_compile`、PowerShell launcher 语法检查通过。代码修复关闭的是资源安全缺口，不代表业务任务完成。
- 修复后目标 worker `company-pc-audiobook-round46-clean-start` 父/子 PID `61108/53352` 于 `23:15:08` 启动；截至 `23:19:37` 尚未 claim，子进程约 `455 MB`、系统可用内存约 `13.31 GB`，保持安全等待，未重复出现内存峰值。不要在新 ready 出现前宣称 clean-start 音频闭环完成。
- 证据：`docs/tts/evidence/audiobook-e2e-20260904/round47-analysis-memory-gate.txt`；代表 `5/5`、新任务音频/下载/播放、49/49、跨窗口/进程/低存储/断电恢复、人耳 QC、角色复核和 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-05 Round48：VoiceDesign 模型加载前资源门禁（Worker-only）

- 复查发现 `_ensure_custom_voice_bible()` 直接加载 VoiceDesign，绕过了本轮新增的 analysis loader 门禁；这会让整书分析后的全 custom voice 阶段仍可能造成公司电脑内存峰值。
- 已按 TDD 增加 `voice_design_model_load_pressure()`、`AUDIOBOOK_VOICE_DESIGN_MODEL_MEMORY_RESERVE_GB` 和 `_load_voice_design_model(guard)`；VoiceDesign loader 只有在 lease/resource checkpoint 与 6GB reserve 通过后才调用。压力命中时 retryable 等待，不硬杀、不伪造 ready。
- 回归：Worker `211/211`、`py_compile`、通用 launcher PowerShell 解析通过；证据 `docs/tts/evidence/audiobook-e2e-20260904/round48-voice-design-memory-gate.txt`。
- 当前 Round46 clean-start worker 是修复前启动的旧进程，仍需自然结束；安全重启后还要现场证明门禁、分析/固定声线、章节 ready、MuMu 同步播放和故障恢复。整体仍 `NOT_SIGNED_OFF`，本轮没有 Android 新包或 public release。

### Round48 续跑现场（2026-09-06）

- 新 clean-start 任务已完成 49 章整书分析、`all_custom` locked voice bible 和 9 个独立参考声部；第 1 个代表章已生成 MP3 并在 MuMu `127.0.0.1:16384` 刷新后同步，详情显示“已下载，可播放”。
- 第 1 章音频 33 segments/334.72 秒/2,204,712 bytes，SHA `8242B90B03272F13B031EE9891BD06EC3911A897D81DF9AE136206FDC2BC327D`；MuMu MediaSession `state=3`、`error=null`，界面显示正在播放和约 `00:08/05:34`。这只关闭新任务第1章技术下载/播放，不代表 5/5、49/49 或人耳 QC。
- worker 仍继续下一个代表章，CPU 推理每批约 3 分钟；现场内存约 4.25–5.42GB 可用，未再次出现 0.59GB 峰值。VoiceDesign 新门禁仍需安全重启后的现场证据，旧进程不热加载本轮代码。

### Round49：Sites 下载扩展名修正（2026-09-06）

- 公开 Sites version 43 已部署成功；source commit `fec2b2a09d012174b71836a8c1e2bb0e580e88a3`，变更为依据 `audioContentType` 为章节下载选择 `.mp3` 等实际扩展名，不再固定 `.wav`。
- Sites 全量测试 `60/60`、lint、build 通过；证据见 `docs/tts/evidence/audiobook-e2e-20260904/round49-download-extension.txt`。
- 本轮不改变 Android `v0.9.17/104` 候选、worker 运行配置或当前新任务状态；整体继续 `NOT_SIGNED_OFF`。

## 2026-09-05 Round45：v0.9.16 首屏可发现性、5/5 代表章与 MuMu 缓存恢复

- 现场复盘确认，用户看到“等待数小时仍未成功返回”的一个直接原因是听书页先渲染本地导入区，远端后台有声书卡片被推到首屏以下；服务端目标书当时已经存在。Android 新增 `AudiobookShelfSection`/`audiobookShelfSectionOrder()`，有远端书时先显示“后台有声书”，再显示“从已下载小说导入”。
- Android `versionName=0.9.16`、`versionCode=103` 已构建并安装到 MuMu `127.0.0.1:16384`。首屏直接看到《微微一笑很倾城》、`代表章节进度 100% · 5/5 个代表章节可听（全书 49 章）`，无 token/令牌、代理、模型或线程输入；新增布局测试先红后绿。
- 当前公开 Site v42 的真实书籍事实为整本分析后的 49 章 manifest，代表目标 `1/11/13/15/49`，`preview_ready`、ready `5/5`、failed `0`；其余44章 `NOT_SELECTED`。这不是 49/49 整本完成，不能将代表试生成写成全书交付。
- 第1/11/13/15章服务端和手机缓存为 WAV，第49章为 MP3；五章本地 bytes/SHA 与远端 metadata 一致。删除第49章本地 MP3 后按普通用户重入/刷新/等待同步，文件恢复为 `22,754,880` bytes，SHA `0a1368697d367fe22b56e22c2ab4c9ef2fee569bd46e7dda83e029702e623986`，详情显示“已下载，可播放”。
- 第49章 MuMu 前台播放 MediaSession `state=3`、`error=null`；退到后台后 position 继续增加；重新进入显示正在播放/历史位置，snapshot 保留 history。Worker `207/207 OK`、py_compile、pip check；Android `503/0/0/8 skipped`；Debug/Release assemble、aapt2、apksigner v2 单 signer、zipalign 均通过。
- 当前根目录只保留 `老婆的小营地-debug-v0.9.16.apk` 与 `老婆的小营地-v0.9.16.apk`；旧 v0.9.15 已归档到 `artifacts/releases/legacy/2026-09-05-v0.9.15-before-audiobook-shelf-order/`。候选校验和现场证据见 `artifacts/candidates/2026-09-05-audiobook-shelf-order-v0.9.16/` 与 `docs/tts/evidence/audiobook-e2e-20260904/`。
- 项目 Worker 当前使用显式公司代理 `127.0.0.1:7897`，已完成目标书后常驻空闲等待；一次连接重置由既有重试记录，跨夜稳定性仍未证明。八个隔离角色本轮已分别派发复核，但编排层虽返回 completed，最新 `read_thread` 正文为空；按规范记为回执不可审计，不从空回执推断 PASS。
- 仍开放：clean-start、物理手机/锁屏耳机、五章编辑/老听客人耳听感、49/49 全书、跨 08:30/18:30、进程崩溃/重启、低存储/断电恢复、自动拉起/告警/回滚和 Android public release。总体继续 `NOT_SIGNED_OFF`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

### Round45 补充：代表音频自动质量门（2026-09-05）

- 五个代表章的 quality endpoint 均 HTTP 200，artifact SHA/bytes 与 manifest 一致；技术报告包含 ITU-R BS.1770-5、locked-reference 角色响度校准和约 -3 dBTP 真峰值门。
- 报告仍保留 `manuscript_editorial=pending`、`subjective_listening=pending_human_listening`，所以自动质量通过不等于声音最终签收。证据：`docs/tts/evidence/audiobook-e2e-20260904/round45-quality-gate-snapshot.txt`。

## 2026-09-05 Round44：短代表章优先、profile dtype 与真实推进

- Site v42 已公开部署成功，claim 顺序改为 analysis 优先、selected 代表章优先、短源文本优先、ordinal 稳定排序；Site 测试 59/59、build 通过。此举针对长章49约236个180字块导致14分钟仅到7%的现场问题。
- Worker 当前完整档为 float32/12线程/batch4，工作时段 throttle 为 bfloat16/6线程/batch1；custom voice 仍为 x-vector-only 和锁定参考音频。线上分块80字、generation cap256。Round43 中“完整档 bfloat16”的历史描述被本轮实测配置修正为 throttle 档。
- 最新 worker 父/子 19104/14736 00:45领取第11章，Site真实进度从5%到9%再到14%，书籍从41%到43%，ready仍为2/5。进度有新时间戳，证明控制面真实推进；尚未把它写成章完成。
- Worker 最新全量 204/204、Android 501/0/0/8 skipped；Site v42部署、Android v0.9.15/102 MuMu无token/已完成章播放后台重入的旧证据仍有效。新代表章下载播放、人耳/编辑QC、跨时段/故障恢复、实体手机和Android public release仍OPEN，整体NOT_SIGNED_OFF。
- 详细本轮证据：docs/tts/evidence/audiobook-e2e-20260904/round44-queue-priority-float32-bounded.txt。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-04 Round40：MuMu 后台播放与进度保持现场回归

- 在 v0.9.14/101 的真实《微微一笑很倾城》详情页，UI hierarchy 定位点击第 1 章后进入 `正在播放`，显示 `Part 1 被抛弃了`、`00:05 / 05:10`。
- MuMu 退到 Android Home 后，`com.lovelyreader` Media3 session 仍为播放状态 `state=3`，metadata 和进度继续推进；回到应用显示 `01:10 / 05:10`，随后暂停后为 `state=2`、`speed=0.0`。当前 PID `10813` 无 `FATAL EXCEPTION`/`AndroidRuntime`。
- 该现场证据只覆盖 MuMu 的后台播放/进度保持/暂停，不替代物理手机锁屏、通知栏、蓝牙耳机/线控测试。真实后台生成仍因模型加载内存安全线等待，代表目标 2/5，整体 `NOT_SIGNED_OFF`。
- 证据：`docs/tts/evidence/audiobook-e2e-20260904/round38-android-resource-wait-and-package.txt`；测试矩阵与任务变更记录已追加 Round40。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-04 Round41：重启重入与验收闸门补证

- 对 v0.9.14/101 的 MuMu APP 执行 force-stop 后重启，按正常“听书”路径完成后台查询，列表滑动后重新打开《微微一笑很倾城》；详情仍显示资源等待、2/5、后台已刷新，第 1 章仍可播放且显示 `继续听到 33%`。
- 重新跑用户验收闸门后，候选 SHA、安装 SHA/version、当前 2/5 快照和 UI 文案扫描均 PASS；总体仍按规范为 `UNVERIFIED`，因为没有 clean-start proof，不把重启重入证据冒充首次安装证据。
- 该轮补充了进程重启后的状态恢复证据，但代表任务仍因 TTS 模型内存安全线等待，未新增 ready 音频；整体 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-04 Round38/39：资源等待详情修复与 v0.9.14/101 本地候选（未正式发布）

- Android 已按版本递增到 `versionName=0.9.14` / `versionCode=101`；根目录只保留 `老婆的小营地-debug-v0.9.14.apk` 与 `老婆的小营地-v0.9.14.apk`，候选校验在 `artifacts/candidates/20260904-resource-wait-ux-v0.9.14/checksums.txt`。Debug SHA-256 为 `B893797EB117AD999D545F40E843A2AB93D9C361D475FFF2AE7CE225FF68704F`，Release SHA-256 为 `D7E729A1F6F02C3723A7253681964A97C2C6810755CDD0ADCEBFE403A9035C0F`。
- Android 单元测试 `500/0/0/8`，Debug/Release 构建、aapt2、apksigner v2 单 signer 和 zipalign 均通过；MuMu `127.0.0.1:16384` 安装并运行 v0.9.14/101。详情页现场显示“等待设备资源”“等待设备资源 · 2/5 个代表章节可听（全书 49 章）”和明确的资源恢复说明，无 token 输入；截图/XML 见 `docs/tts/evidence/audiobook-e2e-20260904/`。
- 真实目标书仍是《微微一笑很倾城》49 章整本源文、`all_custom`、专门男声旁白、8 个固定角色声部；代表目标仍为第 `1,11,13,15,49` 章，当前 ready 仍 `2/5`（1、15），不是整本 49/49。未选章节明确显示“不生成”。
- Worker 当前 launcher/child 为 `22308/20820`，代理 `37144` 保留；全量回归 `196/196`，`py_compile` 通过。工作时段 `08:30–18:30` 使用 throttle；TTS loader 前增加 `6 GB/15% + 8 GB reserve` 的模型加载门禁。2026-09-04 09:27:32 主机总内存 31.72 GB、可用 6.72 GB，worker 按策略常驻等待并释放/不加载重模型，日志重复记录 retryable resource wait。
- Sites v41 source commit `4158af5da7a56efd2590b0fb6aba894f25234bff` 已 push，deployment `appgdep_6a9a1495f4948191a00f4199302a4b21` succeeded；Site `58/58`、lint/build 通过。`RESOURCE_MEMORY_PRESSURE` 不消耗 attempt，Android 按章节事实合并显示资源等待。
- 本轮已请求 PO、UX、QA、QC、架构、Ops、老听客和普通用户八个隔离角色独立复核当前证据，但编排接口返回的最新回执为空；空回执不计为签收。可读的上一轮独立结论和本轮客观门禁仍共同指向 `NOT_SIGNED_OFF`。
- 记录已同步至 `docs/tts/task-change-report-20260903.md` Round38/39、`docs/tts/bug-experience.md`、`docs/tts/test-matrix.md`、`docs/tts/architecture.md`、`docs/tts/detailed-design.md`、`docs/tts/decision-log.md`、`docs/tts/audiobook-production-standard.md`、`docs/tts/PRD.md` 和 `docs/tts/release-record.md`。本轮仍未进行 Android source push、tag、公开 APK 发布；本项目未写入任何 worklog、timesheet 或工时系统。

### 当前必须继续完成

- 资源恢复后真实重新 claim、渲染、上传/complete、手机自动下载并播放第 11、13、49 章，达到代表 `5/5`。
- 证明跨 `18:30/08:30`、租约到期接管、进程死亡/重启、低内存、低磁盘和长时运行恢复。
- 对当前版本实际音频做原文对照、固定音量耳机/外放/低音量试听和老听客 QC；技术测试不能代替听感。
- 完成 Android public Release 的 source push、签名资产、tag、updater manifest/checksum、公开下载与安装验证。当前整体仍 `NOT_SIGNED_OFF`。

## Latest Update: 2026-09-03 Round26 — 真实目标书进度修复与批量 worker

- 《微微一笑很倾城》已由 App 缓存整本上传并在公司电脑本地完成整本分析：source SHA-256 `b42fd53ac87ac74f3e23985c2912f2d161a15e0819da4991cf788ff44a219ba6`，49 章，manifest revision 1；voice bible `all_custom`，专门成年男声旁白 + 8 个固定角色声部，全部 `voice_design_clone`，没有 preset 回退。
- 当前确定性代表章节为 `1,11,13,15,49`。第 1 章已真实上传，WAV 24 kHz mono、14,903,084 bytes、SHA-256 `7024b4a84806d112c0d8ae879e6465d777adac72a3342b497a457235bce66a22`、时长 310.48 秒；技术 QC 通过但原文编辑/主观听感 pending。第 1 章已在 MuMu 完成刷新、下载、播放、Home 后后台播放、重新进入并恢复约 18% 历史进度。
- 真实目标书曾因旧 worker 逐段 CPU 推理在第 11 章长时间停留 `processing/5%`；已精确停止旧 PID、用原 lease 提交可重试重排队，不影响第 1 章已完成产物。worker 新增片段进度、同锁定 custom voice 批量推理、2048 token ceiling；当前 batched worker `company-pc-c91-real-book-20260903-batched` 运行中，参数 batch=4、MAX_CHUNK_CHARS=400。当前第 13 章 active，现场验证尚未完成。
- Sites v31 commit `be19e1197bbc617298f9aedb24309b393b1ea9eb` 已 push，保存为 version 31 并部署到公开站点 `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`；v31 heartbeat 重新发布活动 task 进度。Sites fresh `50/50`、lint/build 通过。
- Android 当前候选为 versionName `0.9.6` / versionCode `93`，root 仅保留 versioned Debug/Release pair；Debug SHA `19710F8B...FC57B`，Release SHA `D39DBB16...3EEF5`。Android fresh ASCII full suite `494/0/0/8`；当前仅本地候选，不称 public Android release。
- Round26 暂不签收：其余代表章、原文编辑 QC、主观人耳听感、CPU 长时稳定性、进程死亡/断电/低存储业务恢复、正式 Android Release 和最终八角色放行仍未完成。相关记录见 `docs/tts/decision-log.md`、`bug-experience.md`、`test-matrix.md` 和 `team-audit/`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-01 Round25 follow-up — 八角色变更后复核

- 在当前候选绑定、末章代表音频播放和 worker OS 单实例锁完成后，PO、UX、QA、QC、架构、Ops、老听客、普通用户重新独立复核；最新整合见 `docs/tts/team-audit/reflection-round25-followup-20260901.md`。
- Follow-up verdict：Architecture/PO/QC/Ops `NOT_SIGNED_OFF`；QA/UX/老听客/普通用户 `CONDITIONAL`；总体仍 `NOT_SIGNED_OFF`。
- 复核确认当前候选与 MuMu `127.0.0.1:16417` installed/pulled APK SHA/bytes 一致，Worker 有效回归 `129/129 OK`，Android fresh `481/0/0/8`，Sites fresh `34/34` 且 `TEST_EXIT=0/LINT_EXIT=0`，101 章正 revision 分页和第 101 章代表音频可播放。
- 仍未完成真实长篇全书音频、人耳听感/原文 QC、任务进程死亡后的业务恢复、断电/低存储/长时压力、任务 lease/CAS、可靠性与签名 Release；独立双 PID 竞争和锁层强制终止接管已验证；本项目没有写入任何 worklog、timesheet 或工时系统。
- r5 后八角色独立复核文件为 `docs/tts/team-audit/*-20260901-round25-kill-followup.md`；锁层 PASS，但总体仍 `NOT_SIGNED_OFF`，不把锁释放测试扩大解释为真实任务恢复或声音交付完成。

## Latest Update: 2026-09-01 Round25 lock regression — 独立 PID 竞争证据

- `test_workspace_lock_is_exclusive_across_independent_processes` 使用两个独立 Python PID 验证同一工作区锁：首进程持锁并发布 ready marker，竞争进程收到 `WorkerAlreadyRunning` 并退出 0；首进程释放后测试正常退出。
- fresh `.venv` 显式 worker 回归为 `128 tests / 0 failures / 0 errors / OK`，日志为 `docs/tts/evidence/audiobook-e2e-20260831/round25-manifest-revision-lock/worker-full-venv-r4.log`。
- 该证据只关闭“独立 PID 竞争未验证”，不等于进程被 kill 后任务恢复、断电事务、低存储、长时容量或服务端 lease/CAS 已通过；总体继续 `NOT_SIGNED_OFF`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-03 Round26 follow-up — 工作时段资源保护

- 用户反馈公司电脑 Python 曾达到约 61% CPU、约 8.6 GB 内存。整书 worker 现默认使用本机资源时间窗 `18:30–次日 08:30`；`08:30–18:30` 期间不 claim、不加载文本/TTS/VoiceDesign 模型，只低频等待。Windows 进程优先级配置为 `below_normal`。
- 新增 `lovely-reader-tts-worker/resource_window.py` 和 `test_resource_window.py`；全 worker `.venv` 回归最新为 `166 tests / 0 failures / 0 errors`。受限 `--once` 现场日志确认未访问 Sites claim；常驻 batched worker PID 38284 工作集约 4.8 MB，日志记录 `18:30-08:30` 与下次允许时间。
- 处理中的模型调用无法安全硬中断；窗口切换后在当前 batch/segment 返回时暂停，使用 retryable failure 回队并释放 TTS runtime。当前真实书其余代表任务保持 queued，不能在白天为了生成而绕过策略；第 1 章 ready 与手机播放证据不变。
- 资源窗口只关闭白天资源门禁，不等于夜间长时稳定性、跨窗口任务恢复、最终音频人耳/编辑 QC 或 Android public Release 已完成。总体继续 `NOT_SIGNED_OFF`；各角色最新复核必须以独立输入追加，不能自动升级旧 verdict。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-03 Round26 follow-up — 白天 stale queue 用户状态与 v0.9.9/96

- 复盘发现 worker 在白天 claim 前等待时，`accepted/queued` 任务仍可能被手机显示成普通排队。Site 与 Android 增加一致的保守兜底：本地 `08:30–18:30`、活动任务时间戳停滞至少 10 分钟时显示“等待空闲时段”，并说明今天 `18:30` 后继续处理；服务端 `waiting_resource_window/RESOURCE_WINDOW_CLOSED` 仍优先，轮询不因兜底停止。
- Site source commit `8d671edaaffb4152d42d5bc657a1a93df1f1ebf1` 已 push，保存并部署为 version `34`，deployment `appgdep_6a98ed70f1ac8191b12bdbb02669cb6d` succeeded；公开地址 `https://lovely-reader-voice-lab.icarus-heeya.chatgpt.site`，access mode public。Site `52/52`、lint/build、health `200`。
- Android 当前本地候选已正常递增为 `versionName=0.9.9/versionCode=96`；根目录 Debug SHA `97FE682452E264DAC9DD8390EA6CDB4777FEB5E64E334ABA7A37DA501E6FCDCD`，Release SHA `15043502DE3B2398C6CAFC14370318556FDDC800BCADC1D74FA6BC57418D1B32`。full suite `497/0/0/8`，Release v2 signature true/one signer。
- MuMu `127.0.0.1:16384` + proxy `10.0.2.2:7897` 已安装并绑定 v0.9.9 Debug；installed/pulled base bytes/SHA 与候选一致。听书页自动 HTTPS 连接成功且没有 token 输入。当前会话没有对应远端 stale task 的详情，故兜底文案仅有单测证据，不把基础页面截图写成 live fallback 验收。
- Worker `.venv` 当前 `167/167 OK`；白天常驻 worker 未 claim/未加载模型，launcher PID `38284` 约 4.3 MB、实际 child `28460` 约 81 MB；第 1 章 ready，其余代表任务仍 queued 等待 `18:30` 后。
- 独立 PO/UX/QA/QC/架构/Ops/老听客/普通用户复核已追加到 `docs/tts/team-audit/*-20260903-resource-window-followup.md`；由于没有夜间生成、人耳 QC、跨窗/故障恢复和 public Android release，整体 verdict 保持 `NOT_SIGNED_OFF`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-03 Round26 follow-up — 工作时段低资源持续生成修正

- 用户明确的资源目标不是白天完全停机，而是 `08:30–18:30` 仍保持异步生成、降低到约一半资源以不影响办公；默认策略已从 hard pause 改为 `throttle`，夜间 `18:30–08:30` 使用 `full`，`pause` 仅作显式应急覆盖。
- Worker 新增 `ResourceBudget` 与三态 profile：本机脚本完整线程 12、白天线程 6，完整/白天 TTS batch 4/1，白天安全边界让出 0.5 秒，白天无任务轮询 15 秒；Windows 尝试限制约一半逻辑 CPU affinity，并保持 below-normal priority。
- 动态线程预算通过 `AUDIOBOOK_RUNTIME_CPU_THREADS` 同时传入 Qwen TTS 和本地文本模型；Site/Android 白天活动任务显示“低资源处理中”。worker `.venv` 全量为 `177/177 OK`，Site `52/52`、lint/build、py_compile 通过；真实模型 CPU/内存曲线、夜间恢复和跨窗业务恢复仍待现场证据。

## Latest Update: 2026-09-04 Round42 — worker 显式代理修复与现场恢复

- 继续巡检发现 worker 在 Sites claim 阶段连续出现 `WinError 10061`。诊断确认当前公司代理 `127.0.0.1:7897` 可用并经 Sites `/api/health` 返回 HTTP 200；MuMu 诊断端口 `127.0.0.1:18777` 虽监听，但其上游 `127.0.0.1:7078` 已关闭，经 18777 返回 HTTP 502。
- 根因边界：SitesClient 原来依赖 Python/Windows 隐式代理，代理切换或旧诊断 helper 生命周期可能让 worker 沿用失效 localhost 路径。修复为显式 `SITES_PROXY_URL` + 独立 HTTP/HTTPS ProxyHandler；当前 batched launcher 绑定 `http://127.0.0.1:7897`，不修改系统代理、不停止 7897 或无关进程。
- 按 TDD 新增显式代理回归：旧代码先因不接受 `proxy_url` 失败，实现后目标测试 `2/2 OK`；Worker 全量 `197/197`、`py_compile` 通过。
- 只重启已确认的 audiobook worker 父子进程；新进程 `34868/48680` 成功 claim 目标任务并在模型加载前按内存安全线等待，重启后未再出现 `WinError 10061`。一次 SSL EOF 被既有重试恢复，长期代理稳定性仍未证明。
- 办公时段约 15 秒采样中，TTS 子进程 working set 约 `440.1 MB`、CPU `0.00–0.17%`、below-normal；这只证明等待期间没有加载重模型，不是长时资源曲线。
- 当前业务仍为《微微一笑很倾城》49 章中代表目标 1、11、13、15、49，ready 2/5；资源恢复后的新音频、5/5、整本、下载播放、人耳/编辑 QC、八角色最终签收与 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。证据：`docs/tts/evidence/audiobook-e2e-20260904/round42-worker-proxy-fix.txt`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。
- 不能把约 50% 解释成硬 CPU 百分比或内存减半；完整音频、人耳/编辑 QC、长时/故障恢复和 Android public Release 仍未签收。项目没有写入任何 worklog、timesheet 或工时系统。

## 2026-09-04 Round43：bfloat16/x-vector 运行档、Android 状态修正与 v0.9.15/102

- 当前 32 GB CPU 公司电脑的整书 Worker 已切换为启动脚本显式配置 `AUDIOBOOK_TTS_DTYPE=bfloat16`、`AUDIOBOOK_CUSTOM_VOICE_CLONE_MODE=x_vector_only`，仍使用整书 voice bible 锁定的自定义 narrator/角色参考音频；不恢复预置声色。float32 模型加载探针曾将可用内存压到 1 GB 以下，bfloat16 加载探针约 1.5 GB working set 并成功进入渲染；ICL 64/8 token 探针在 12–14 分钟仍无 WAV，记录为当前 CPU 慢路径限制。
- Worker/manifest 固化 custom voice 的 clone inference mode，batch 不跨角色、参考音频或模式混批。全量 Worker 测试 `199/199`、py_compile 和启动脚本语法检查通过；x-vector-only 技术冒烟样本不等于人耳质量签收。
- 修复 Android 详情页状态合并：第 11 章等待内存而第 13 章仍 rendering 时，活动渲染优先，书籍显示“正在生成音频”；无活动渲染时才显示书籍级“等待设备资源/等待空闲时段”，等待章节的行级事实保留。TDD 先红后绿，Android 全量 `501/0/0/8 skipped`。
- Android 版本按用户要求递增为 `0.9.15/102`，Debug/Release 已构建、签名检查并安装 MuMu `127.0.0.1:16384`；全局代理 `10.0.2.2:7897`，首页“听书服务已连接”，无 token/代理/模型输入，详情刷新显示 `正在生成音频`、`41% · 2/5`。旧 v0.9.14 pair 已归档，根目录只保留 v0.9.15 pair。
- 最新真实 Sites 状态仍是《微微一笑很倾城》49 章代表目标 `1/11/13/15/49`，ready `2/5`、failed `0`，第 11 等待资源、第 13 rendering、第 49 pending；5/5、49/49、长时/跨窗/进程恢复、人耳/编辑 QC、物理手机和 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。详细证据：`docs/tts/evidence/audiobook-e2e-20260904/round43-bfloat16-xvector-capacity.txt`。
- 本项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-03 Round26 follow-up — v0.9.10/97 与 Site v35 验证

- Site source `3cf3da6582d61965c421a04515ba8010ce5c3d8f` 已 push，保存为 v35 并公开部署成功；`/` 和 `/api/health` HTTP 200。白天 stale 状态已改为“低资源处理中”。
- Android `0.9.10/97` Debug SHA `AD822871AFF18EEFB3F57FC9567D7EAA93D7F5084FA58B0EE2F7936199F56CEA`，Release SHA `5A6784552428737AD01359594A7C179B3323AF22C25922B38289AE47200720FE`；全量 `497/0/0/8`，aapt2、v2 单签名和 zipalign 均通过。MuMu `127.0.0.1:16384` 安装包与 Debug 候选 SHA/bytes 一致，听书页无 token 输入。
- 这仍是本地 Android 调试候选，不是 Android public release；真实 worker 的 CPU/内存曲线、夜间恢复和跨窗业务证据仍待补齐。项目没有写入任何 worklog、timesheet 或工时系统。

## Latest Update: 2026-09-03 Round28 follow-up — claim 前内存压力保护

- 保留白天 `throttle` 持续推进：worker 在所有 profile 的下一次 Sites claim 前读取 Windows 物理内存余量；默认可用内存低于 `6 GB` 或总内存比例低于 `15%` 时不领取新任务，释放缓存的重型 TTS/文本模型并按当前 profile 低频重试。已开始的模型调用自然完成，不硬杀、不伪造半成品。
- 新增 `SystemMemorySnapshot`、`ResourceBudget.memory_pressure()`、claim 后安全边界复检、固定目标书响应身份校验，以及环境变量 `AUDIOBOOK_THROTTLE_MIN_FREE_MEMORY_GB/FRACTION`；两个内存阈值不能同时为 0，`.venv` 全量最新为 `190/190 OK`。该保护是前置安全门禁，不是内存硬上限，不能声称工作集减半。
- 现场曾出现约 `3.90 GB` 可用内存，稍后恢复至约 `10.34 GB`；这解释了为何需要内存门禁。当前活动常驻 worker 子进程是在本次代码改动前启动的，不能强杀套用新逻辑；安全结束后需重启并验证压力触发、释放和恢复后 claim。
- Android `0.9.10/97`、Site v35、MuMu 连接和无 token 证据不变；没有新的 Android public release。长时办公曲线、夜间 full/跨窗 lease、故障恢复、其余代表章以及编辑/人耳 QC 仍 OPEN，整体 `NOT_SIGNED_OFF`。本项目没有写入任何 worklog、timesheet 或工时系统。
- Round29 独立团队复核记录见 `docs/tts/team-audit/reflection-round29-20260903-memory-guard-final.md`；PO、UX、QA、QC、架构、Ops、老听客和普通用户均未把当前状态升级为最终通过。新鲜隔离复核派发未返回可用完成回执，未计入证据；后续仍以当前源码、现场 PID 和可审计测试为准。

## 2026-09-03 Round31：代表章节进度误导修复

- Android `0.9.11/98` 本地候选已构建并安装到 MuMu `127.0.0.1:16384`。`AudiobookBook` 贯通 `generationScope`/`generationTargetCount`，章节模型接入 `NOT_SELECTED`；代表试生成显示 `2/5 个代表章节可听（全书 49 章）`，未选章节显示“未纳入本次试生成/本次不生成”。
- TDD 新用例先红后绿；Android 全量 `:app:testDebugUnitTest`、Debug/Release assemble、aapt2、Release apksigner 通过。第 15 章在 MuMu 可下载/播放，媒体会话 state=3。
- 根目录仅保留 v0.9.11 Debug/Release pair；v0.9.10 pair 归档到 `artifacts/releases/legacy/2026-09-03-v0.9.10-representative-progress-fix/`；候选校验见 `artifacts/candidates/2026-09-03-representative-progress-fix-v0.9.11/SHA256SUMS.txt`。
- 不能把本轮客户端修复描述为整本生成完成：真实后台仍代表目标 5 章、ready 2 章；夜间恢复、失败重试、worker 新内存门禁 live 重启、音频人耳/编辑 QC、长时/故障恢复和 Android public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。

## 2026-09-03 Round32：代表模式终态文案与 v0.9.12/99

- 修正 Android `PREVIEW_READY` 文案：其他章节未纳入本次试生成；需要整本音频必须重新选择 full scope。新增 `audiobookPreviewReadyHint()`，单测先红后绿。
- Android `0.9.12/99` 已构建、签名检查并安装 MuMu；全量单元测试为 `499/0/0/8 skipped`。新鲜 MuMu 证据显示真实任务 `43% · 2/5 个代表章节可听（全书 49 章）`，第 15 章可播放。
- 根目录仅保留 v0.9.12 Debug/Release pair；v0.9.11 pair 已归档至 `artifacts/releases/legacy/2026-09-03-v0.9.11-representative-progress-fix/`；候选校验见 `artifacts/candidates/2026-09-03-representative-copy-explanation-v0.9.12/SHA256SUMS.txt`。
- 这次只关闭了文案误导，未改变当前代表生成任务；仍未完成 5 个代表目标、整本 full 流程、失败重试、资源/故障恢复、人工音频 QC 和 Android public release，整体 `NOT_SIGNED_OFF`。

- 19:09 最新 MuMu 刷新将真实任务进度更新为 `44% · 2/5 个代表章节可听（全书 49 章）`；仍只有第 1、15 章本地音频，worker 代表任务尚在运行，不能把进度变化或后台运行当成整本完成。现场记录见 `docs/tts/evidence/audiobook-e2e-20260903/round32-mumu-refresh.txt`。

## 2026-09-03 Round33：首页同步提示修正与 v0.9.13/100

- 听书首页不再显示“后台会继续准备后续章节”，改为引导用户在详情查看真实生成范围与进度；新增 `audiobookSyncedAudioHint()`，单测先红后绿。
- v0.9.13/100 已构建、签名检查、安装 MuMu；首页现场显示新文案，详情/播放路径保持可用。根目录仅保留 v0.9.13 Debug/Release pair，v0.9.12 pair 归档至 `artifacts/releases/legacy/2026-09-03-v0.9.12-audiobook-copy-clarity/`，候选校验见 `artifacts/candidates/2026-09-03-audiobook-copy-clarity-v0.9.13/SHA256SUMS.txt`。
- 最新任务事实仍为 `44% · 2/5 个代表章节可听（全书 49 章）`，本地仅第 1、15 章音频；这不是整本任务，其他章节不会自动扩展。APP 全功能、5 个代表目标全闭环、重试/恢复、人耳 QC 和 public release 仍 OPEN，整体 `NOT_SIGNED_OFF`。

## 2026-09-04 Round34：失效租约停止渲染与内存门禁现场

- 真实书 `微微一笑很倾城` 的 task `4fde1295-2464-4657-ba45-9b1e6fb1a9bf` 租约于 `2026-09-03T14:57:28Z` 到期；旧 Worker 因 heartbeat 失败后仍被进度回调吞掉租约异常，继续推理但片段结果均被服务端拒收。已安全停止项目 Worker `33724/46096`，未停止代理 `37144`。
- Worker 修复：`LeaseGuard` 将 `SitesLeaseConflictError` 转换为 `LeaseLostError`；分析、音色和章节片段安全边界统一校验 lease；渲染进度回调只容忍普通 telemetry 失败，租约丢失直接停止，不再继续下一片段/上传/complete。
- 新增两个回归用例，目标测试 `2/2 OK`，`py_compile` 通过；修复和证据已记录在 `docs/tts/task-change-report-20260903.md` Round34、`docs/tts/bug-experience.md` 的 `BUG-20260904-STALE-LEASE-CONTINUES-RENDER`、`docs/tts/test-matrix.md` Round34、architecture/detailed-design/decision-log。
- 当前新 Worker PID `53640` 已按当前源码启动；它因机器可用内存约 `0.68–1.22 GB` 低于 `6 GB/15%` 安全线而未 claim/未加载模型，正在低资源等待。恢复后应自动重新领取代表目标 task；不能绕过门禁。
- 代表任务当前仍为 49 章中的 5 个目标，之前 ready 2 个；第 49、11、13 章的真实重试/ready、跨窗口恢复、长时/故障恢复、人耳 QC、八角色放行和 Android public release 仍未完成，整体继续 `NOT_SIGNED_OFF`。

## 2026-09-04 Round35：Worker 全量回归恢复

- 修正白天 throttle 测试的时间 patch 作用域：batch 断言必须在模拟白天期间执行，避免退出 patch 后按真实夜间 profile 读取 full batch，产生测试假失败；产品资源策略未改变。
- Worker 全量回归恢复为 `192 tests / 0 failures / 0 errors`；租约失效安全停止目标用例 `2/2 OK`，`py_compile` 通过。
- 新 Worker `53640/52392` 仍常驻等待，当前系统可用内存低于 `6 GB/15%` 门禁，尚未重新 claim 代表任务；不得把“测试全绿”描述成音频生成完成。
- 代表目标、APP/MuMu fresh progress/download/playback、跨窗/故障恢复、音频主观 QC、团队最终签收和 Android public release 继续 OPEN；本项目未写入任何 worklog、timesheet 或工时系统。

## 2026-09-04 Round36：修复 stale render recovery 投影缺失并完成 live 回队

- 现场根因已定位：`recoverStaleRenderLeases()` 的 candidate SQL 用 WHERE 筛选 `t.kind='render_chapter'`，但 SELECT 漏了 `t.kind`；统一 helper 因类型缺失返回 false，attempt 未耗尽的第 49 章 task 被静默跳过，长时间保持 `rendering/38%`。
- Site 已补回 `t.kind`，移除临时 `debugRecovery` 参数、诊断 SQL、公开 debug header；测试新增投影断言。source commit `46ce1afbcd17c7c4be15903675e6489674a2620d`，公开 Site version 39 / deployment succeeded；Site `55/55`、lint/build 通过。
- MuMu 通过已有匿名会话触发真实刷新后，task `4fde1295-2464-4657-ba45-9b1e6fb1a9bf` 从 `processing/rendering 38%` 安全回队为 `queued/retry_wait 0%`，错误码 `LEASE_EXPIRED_REQUEUED`；chapter 49 为 `pending/0%`，book 为 `generating/queued/40%`，用户不需要输入 token。
- 当前安装的 Android `0.9.13/100` 在 MuMu 重新刷新后仍能播放第 1 章；MediaSession `state=3`、`error=null`，回到后台再重入后显示 `正在播放` 和约 46% 的历史进度。该证据只证明已 ready 章节的播放/后台/进度回显，不增加 ready 章节数量。
- 新 Worker `53640/52392` 仍因可用内存约 `0.28–1.67 GB` 低于 `6 GB/15%` 安全门禁而等待，未 claim/未加载模型。代表 ready 仍 2/5；第 11、13、49 章重新生成、跨窗/进程故障恢复、音频人耳 QC、团队最终放行和 Android public release 继续 OPEN，整体 `NOT_SIGNED_OFF`。
- 八角色本轮独立复核已完成并记录于 `docs/tts/team-audit/reflection-round36-20260904-stale-render-recovery.md`；只签收 stale recovery 增量，不签收 5/5、整本、声音质量或最终 APP 交付。最新回执正文未由编排接口暴露，因此不据此推断额外放行。
- 本项目没有写入任何 worklog、timesheet 或工时系统。
## 2026-10-02 Round39：排行榜完整范围与筛选发现性复测（v0.9.26/113）

- 用户指出中国筛选下没有 2025/2024/2026，复盘承认此前把首屏高分样本和折叠筛选当作完成，验收不合格。本轮使用完整 IMDb `numVotes>=100`、1950–当前、支持类型范围，目录 `catalogVersion=2026-10-02.6`、`itemCount=200,499`、fingerprint `ef2ca9a8641f3898`。
- 静态与真实设备：CN 2026=31、2025=103、2024=112；默认筛选改为展开；MuMu `emulator-5554` fresh uninstall/install v0.9.26/113，2025 实际分页到 101–103，2026/2024 总数分别 31/112，最终 crash buffer 0 行。证据：`docs/ranking/evidence/20261002-v0.9.26-ranking-completeness-mumu.md`。
- 生成器对批准 Wikimedia/TVmaze 海报增加有限重试，并对近期 CN/KR/TH 每个年份首 30 条设置离线缓存预算；长尾无合法图片时继续诚实占位。该改动不读取/写入 audiobook、播放器、片源网络或更新器。
- Python `16/16`；Android JVM `575/0/0/8 skipped`；Debug/Release assemble、aapt2、Release v2 单签名、zipalign 通过。根目录只有 v0.9.26 Debug/Release pair，旧 v0.9.25 已归档至 `artifacts/releases/legacy/20261002-v0.9.25-before-v0.9.26-ranking-completeness/`。
- 角色状态：数据/筛选/分页/稳定性在声明范围内 PASS；长尾真实海报/完整剧情、TMDB/Trakt/豆瓣授权多源、golden 像素和公共 Release 仍 CONDITIONAL/NOT_SIGNED_OFF，不能宣称“全互联网全量完成”或“已发布”。
- 本项目没有写入任何 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-18 Round53（最终交付尾记录）：排行榜资料质量与 v0.9.38/125

- 本轮最终构件：`com.lovelyreader` `0.9.38/125`；目录 `200,509` 条，覆盖计数为中文名 `72,049`、中文简介 `10,805`、合法海报 `12,352`、地区 `129,197`、评分 `200,508`。
- 自动化与构建：Python `35/35`；Android JVM `603/0/0/8 skipped`；clean 后 Debug/Release 构建成功；aapt2、apksigner v2、zipalign、jarsigner 通过；`git diff --check` 通过。
- 交付布局：根目录只有版本化 Debug/Release pair；旧 v0.9.37 pair 归档到 `artifacts/releases/legacy/20261018-v0.9.37-before-v0.9.38-ranking-quality/`；候选清单在 `artifacts/candidates/20261018-ranking-quality-v0.9.38/`。
- 门禁：SA、UX、QA 自动化、QC-数据、QC-回归在各自范围内通过；本轮无在线 MuMu，设备黑盒和逐像素 golden 不签收；源码 push/tag/public Latest Release 仍需公网链路验证。
- 本项目没有写入任何 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-18 Round53：排行榜资料质量契约与 v0.9.38/125 候选

- 触发：PO 最终交付复核要求把资料缺失、中文优先、海报占位和视觉可读性变成可测量契约，而不是只看少量截图。
- 实施：新增 `RankingQuality.kt`、`RankingQualityTest.kt`；目录生成器、catalog index、Android snapshot 增加 `metadataCoverage`；排行卡片显示当前页/全量资料质量、无障碍内容描述和视觉契约（84×112dp 海报、48dp 最小触控、标题 3 行、简介 4 行）；版本升为 `0.9.38/125`。
- 当前离线目录 `200,509` 条，覆盖：中文名 `72,049`、中文简介 `10,805`、合法海报 `12,352`、地区 `129,197`、评分 `200,508`。仍明确不是 TMDB/Trakt/豆瓣实时授权全行业榜。
- 验证：Python `35/35`；Android JVM `603/0/0/8 skipped`；clean 后 Debug/Release assemble 成功；aapt2、apksigner v2、zipalign、jarsigner 通过；`git diff --check` 退出 0。
- 交付布局：根目录只保留 `老婆的小营地-debug-v0.9.38.apk` 和 `老婆的小营地-v0.9.38.apk`；v0.9.37 pair 归档至 `artifacts/releases/legacy/20261018-v0.9.37-before-v0.9.38-ranking-quality/`；候选在 `artifacts/candidates/20261018-ranking-quality-v0.9.38/`。
- 角色证据分别位于 `docs/ranking/evidence/2026-10-18-*.md`。本轮 adb 无在线设备，未把历史 MuMu 截图当作本轮通过；逐像素 golden、公共 Latest Release、source push/tag、长尾真实素材和听书长时恢复仍未签收。
- 本项目没有写入任何 worklog、timesheet、ZenTao 或工时报告。

## Round51 - 2026-10-07 - 标题脚本清理与贝叶斯排名分校准 v0.9.36/123

- 用户指出榜单仍显示其他语言，并质疑票数权重过高。现场资产定位确认：`imdb:tt11612120` 原主标题为 `俺と世界の絶望`，`imdb:tt31014115` 的日文 AKA 为 `照明店の客人たち`；旧排序 `（评分−5）×log10(票数)` 产生 10.2/10.1 的超量程 tag。
- 先红测后修复：生成器新增日文假名/韩文脚本过滤与稳定中文覆盖；Android 展示层优先已审核简体 display title，不再按汉字数量从别名抢占；无可信译名继续保留原名。
- 排名改为有界贝叶斯分 `评分×v/(v+1000)+7.0×1000/(v+1000)`，票数只作为置信度；Android/Python 同步，methodology=`imdb-rated-scope-v6-tvmaze-full-index`，catalog=`2026-10-07.1`，资产 200,509 行，fingerprint `9fe61d76761fee19`。
- 资产机械修正 1,848 条标题；`tt11612120`=`甜蜜家园`，`tt31014115`=`照明店的客人们`。Python `31/31`；Android JVM `596/0/0/8 skipped`，Debug/Release assemble 成功。MuMu `emulator-5554` fresh uninstall/install 后进入排行榜、滚动和韩国筛选，PID `6540` 存活，清空 logcat 后无 FATAL/OOM/ANR；候选 SHA、PNG/XML 和日志见 `artifacts/candidates/20261007-ranking-title-score-v0.9.36/`。
- 版本升为 `0.9.36/123`；旧 v0.9.35 APK 已归档至 `artifacts/releases/legacy/20261007-v0.9.35-before-v0.9.36-ranking/`。本候选仍未 source push/tag/公开 Release；本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round49 - 2026-10-06 - 简体中文、实际排名分与海报资产回接 v0.9.35/122

- 用户指出中文榜单仍夹杂英文、海报大量空白，且卡片展示的综合评分与实际名次不一致。根因分别是展示层未从候选标题选择 CJK、部分内置 `.img` 资产未按 canonical id 回接，以及排序使用 `(评分−5)×log10(有效票数)` 而 UI 只显示原始评分。
- 仍未关闭：目录长尾无合法海报、无可靠中文译名的作品、多站授权实时排行、排行榜 golden/无障碍、source push/tag 和 public Latest Release。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。
- 修复：`rankingPreferredDisplayTitle()` 优先已验证简体中文候选并使用 Android ICU 繁简转换（保留回退）；`rankingGenreLabel()` 将已知英文类型转为简体中文；`RankingEntry.rankingScore` 随快照传递，卡片标题前显示仅数字 `x.x` tag，TalkBack 单独保留“排名分 x.x”语义；Bundled repository 回接 `ranking/posters/<safe-id>.img`；RankingPoster 增加 `thumb.wikimedia.org` 白名单和受限磁盘缓存。
- 版本 `0.9.35/122`；目录仍为 200,509 条、IMDb 评分阈值 + TVmaze 公开索引增强快照，不扩大为多站实时行业全量。根目录严格只有 Debug/Release pair：Debug SHA `C7393C7E1FE3448A3B145F0100F5EDB9D66ACB9DFFA28AE0BB4C70B21B949051`，Release SHA `05C3259902780273BC52E02969189B1B9276FE8A8E1B37732C4C2B6365D05323`；v0.9.34 已归档，候选证据在 `artifacts/candidates/20261006-ranking-score-cn-poster-v0.9.35/`。
- 验证：Python `26/26`；Android JVM `594/0/0/8 skipped`；Debug/Release、aapt2、apksigner v2、zipalign 通过。MuMu `emulator-5554` fresh Release 最终 UI tree 可见评分 tag `28.3`、标题《绝命毒师》、中文类型“剧情、犯罪、惊悚”及原始评分 `综合参考分 9.4/10`，内置海报真实可见，封面占位匹配为 0；PID `5889` 存活且无 FATAL/OOM/ANR。
- 仍未关闭：目录长尾无合法海报、无可靠中文译名的作品、多站授权实时排行、排行榜 golden/完整无障碍、source push/tag 和 public Latest Release。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round50 - 2026-10-06 - 评分 tag 数值化与完整繁简转换回归

- 目标缺陷先以红测固定：旧实现的 `排名分 28.3` 视觉 tag 和 `頒獎典禮` 繁体标题各失败；修复后 `RankingPresentationLabelTest` 全部通过。
- 生产实现：`rankingScoreTag()` 只返回格式化数值；`rankingScoreContentDescription()` 单独提供无障碍指标名称。API 24+ 使用 ICU `Traditional-Simplified`，低版本/JVM/异常使用现有确定性字符表回退。
- 交付证据：根 pair SHA 已更新为 Debug `C7393C7E1FE3448A3B145F0100F5EDB9D66ACB9DFFA28AE0BB4C70B21B949051`、Release `05C3259902780273BC52E02969189B1B9276FE8A8E1B37732C4C2B6365D05323`；候选 `artifacts/candidates/20261006-ranking-score-cn-poster-v0.9.35/` 的 canonical UI tree/PNG/logcat/SHA 清单同步更新。
- 验证：Android JVM `594/0/0/8 skipped`；MuMu Android 12 fresh Release 可见 `28.3`、《绝命毒师》、中文类型、真实内置海报，`封面占位=0`，PID `5889`，logcat 无 FATAL/OOM/ANR；未改变 audiobook、播放器、片源网络或更新器。
- 仍未关闭：长尾真实海报、可靠中文身份缺口、多站授权实时排行、golden/完整 TalkBack、source push/tag 和 public Latest Release。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。
- 仍未关闭：目录长尾无合法海报、无可靠中文译名的作品、多站授权实时排行、排行榜 golden/无障碍、source push/tag 和 public Latest Release。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-03 Round40：完整 TVmaze 索引与 v0.9.27/114 本地候选

- 用户指出先前排行数据仍像少量高分样本；本轮把 TVmaze 从前 15 页改为公开索引 0–379 页完整扫描，成功完整阈值缓存，失败页不污染快照。Wikidata poster 可选字段缓存误判也已修复。
- 新目录 `catalogVersion=2026-10-03.7`、`itemCount=200,509`、fingerprint `2ec355db3888af21`、methodology `imdb-rated-scope-v4-tvmaze-full-index`；CN 2026/2025/2024=`32/102/112`；来源组合 IMDb-only 190,461、IMDb+TVmaze 10,026、TVmaze-only 22。
- Android `0.9.27/114` 根目录严格只有当前 Debug/Release pair；最后一次“公开评分排行”首页文案修正后的根 APK SHA 为 `FD4D99E32E693F55E8F11B6884D914E8C3D37E10E5F496ED5BCD9FDC92691B05` / `F1F9BBB6432BB375A6EAEE9776EE7C9BECAEEB47323B7B36D8DC90DD4FB1B6AA`。候选证据在 `artifacts/candidates/20261003-ranking-full-tvmaze-v0.9.27/`。
- 验证：Python `18/18`；Android JVM `576/0/0/8 skipped`，Debug/Release 构建成功；MuMu fresh Release 中国+2025 到 102、2024 到 112、2026 到 32；最终 APK 重新进入排行榜 smoke，首页显示“公开评分排行”，清空后的应用 crash buffer 为空（`crash-final-v4.txt`）。uiautomator 重复注册异常单独归类为工具噪声，不计为应用崩溃。
- 客户端方法语义改为“公开评分”，榜单标签为“公开评分排行”；不再把单一 IMDb 范围 + TVmaze 增强写成综合热度。
- 未关闭：TMDB/Trakt/豆瓣授权实时多源、长尾海报/完整剧情、排行榜 golden 像素、source push/tag/Latest Release。没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round52 - 2026-10-11 - 偏好类型优先与 DFF 海报回接 v0.9.37/124

- 用户要求将喜剧、动作、恐怖、悬疑、惊悚优先排在前面，并指出 DFF 卡片使用确定性占位。实现把 `genrePriority` 加入 `BundledRankingRepository` 行引用排序，规则为 `genrePriority DESC → rankingScore DESC → canonicalId ASC`；多类型命中任一偏好类型进入优先组，过滤、分页和排名分公式保持独立。
- 生成器增加 DFF `imdb:tt30581662` 的审核 TVmaze 海报 override，并随包写入 `ranking/posters/imdb_tt30581662.img`；依据爱奇艺中文目录和百度中文条目的稳定名称，`items.ndjson`、`catalog.json` 现显示简体中文《永远的逝友》，同时保留 `Dead Friend Forever - DFF` 作为搜索标题。
- 目录与版本：`catalogVersion=2026-10-07.2`，`methodology=imdb-rated-scope-v7-preferred-genres-tvmaze-full-index`，`itemCount=200,509`，fingerprint `839d9ffde847eb01`；Android `0.9.37/124`。
- 验证：Python `33/33`；Android JVM `597/0/0/8 skipped`；Debug/Release assemble 成功；MuMu `127.0.0.1:16384` Android 12 / 720×1280 fresh Release，默认前 3 名命中偏好类型，泰国筛选 `718` 条，滚动到第 99 名显示《永远的逝友》及真实随包海报；PID `4622`，清空 logcat 后 FATAL/OOM/ANR 均为 0。证据在 `artifacts/candidates/20261011-ranking-preferred-genres-v0.9.37/`。
- 根目录仅保留当前版本化 Debug/Release pair：Debug SHA `6B3E50C5028DD3808FCBDE4FDC78D6533BDB4C8C6B1D2D540C0B61CC589529FB`，Release SHA `FAA44C6DA4B6D7F8C6A30BE7CFFECA9AF97BD395E1DEF449681972719AF22BF2`；旧 v0.9.36 pair 已归档。候选仍未 source push/tag/公开 Release；全量合法海报、多站授权实时排行、golden/完整无障碍和公共发布仍 NOT_SIGNED_OFF。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-04 Round41：全目录 Wikidata 身份补齐与 v0.9.28/115 本地候选

- 反思确认 v0.9.27 只对近期区域条目发起 Wikidata 新查询，历史作品的国家/中文身份覆盖不足。本轮把缺失身份查询扩展到完整目录，SPARQL 批处理提高到 500；成功空结果负缓存，失败请求不缓存。
- 新目录 `catalogVersion=2026-10-03.8`、`identityMapVersion=imdb-wikidata-p495-akas-tvmaze-wikipedia-zh-v9`、`itemCount=200,509`、fingerprint `5cb35ca35477cee8`；CN 2026/2025/2024=`32/103/112`。缺失国家 71,312、无中文展示名 122,494、无海报 188,201。
- Android 升至 `0.9.28/115`；根目录严格只有当前 Debug/Release pair，旧 v0.9.27 已归档到 `artifacts/releases/legacy/20261004-v0.9.27-before-v0.9.28-wikidata-full/`；候选证据在 `artifacts/candidates/20261004-ranking-wikidata-full-v0.9.28/`。
- 验证：Python `19/19`；Android JVM `576/0/0/8 skipped`；aapt2/jarsigner 通过可用范围；MuMu fresh install 中国+2025 总数 103、末页 102/103，最终 crash buffer 为空。
- 角色结论：声明范围内数据/筛选/分页/稳定性 PASS；多站实时排行、长尾中文/海报/完整简介、golden 像素和 public release 仍 CONDITIONAL/NOT_SIGNED_OFF。没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round42 - 2026-10-04 - v0.9.29 China metadata-priority candidate

- Implemented `localization_candidates()` in `source/ranking-service/build_catalog.py`; the bounded Chinese Wikipedia/controlled poster enrichment budget now prioritizes all eligible CN production rows before recent KR/TH and global tail.
- Regenerated `catalogVersion=2026-10-04.1`, `identityMapVersion=...-v10`, 200,509 rows, fingerprint `c140c95b26ecc3e3`; CN 2026/2025/2024 remains `32/103/112`; no-poster count is 188,167 overall and 1,999 for CN.
- Bumped Android to `0.9.29+116`; Python `20/20`, Android `576/0/0/8 skipped`, MuMu fresh install/smoke and empty crash buffer passed. Root contains only versioned v0.9.29 APK pair; v0.9.28 archived.
- This is still a local candidate. Multi-site live industry ranking, complete real posters/summaries, golden pixel/accessibility evidence, source push/tag and public Release remain open; do not describe it as fully accepted or published.

## Round43 - 2026-10-04 - v0.9.29 allowlisted poster redirect rebuild

- MuMu visual review found that controlled Wikimedia `Special:FilePath` URLs could be rejected before reaching their actual image because the ranking poster loader disabled all redirects. `RankingPoster` now follows at most three HTTPS redirects manually, with Wikimedia/TVmaze/IMDb host allowlisting and `image/*` content-type validation; unknown hosts, HTTP and redirect loops still fall back to an honest placeholder.
- Added `RankingPosterPolicyTest` for both host policy and redirect resolution; Android JVM is now `578/0/0/8 skipped`. Debug/Release were rebuilt and the current root pair is `老婆的小营地-debug-v0.9.29.apk` / `老婆的小营地-v0.9.29.apk` with hashes in the candidate SHA file. Fresh MuMu install/launch after clearing app data left the current crash buffer empty and the process alive.
- Candidate evidence was refreshed under `artifacts/candidates/20261004-ranking-cn-enrichment-v0.9.29/` (`v029s-launch.*`, `v029s-drama.*`, `v029s-ranking.*`, `v029s-cn2025-collapsed.*`, `v029s-crash-final.txt`). This remains a local candidate: multi-site live industry ranking, all-row real poster/summary coverage, golden pixel/accessibility evidence, source push/tag and public Release remain open.

## Round44 - 2026-10-04 - v0.9.30 source-board candidate

- 复盘发现单一“公开评分”入口不能让用户区分综合分与来源分。本轮新增 `aggregate`、`source-imdb`、`source-tvmaze` 三个中文榜单；Bundled repository 按对应 `sourceRatings` 严格过滤，缺失来源不以综合分或零分补位；卡片分数标签随榜单切换。为使能力变更不复用旧游标，目录版本升为 `2026-10-04.2`。
- Android 从 `0.9.29/116` 递增到 `0.9.30/117`。Python 生成器 `22/22`；Android JVM `579/0/0/8 skipped`；Debug/Release assemble、Release v2 签名和 zipalign 通过。
- MuMu `emulator-5554` fresh uninstall/install 重建后的当前 Release，验证追剧→排行榜进入、综合/IMDb/剧集三榜切换、来源榜总数和来源分文案；进程 PID 存活，`v030g-crash-final.txt` 为空。证据在 `artifacts/candidates/20261004-ranking-source-boards-v0.9.30/`。
- 根目录严格只保留 `老婆的小营地-debug-v0.9.30.apk`（当前重建 SHA-256 `FEEEB07089ACDA040053D5A64321C3C9623EBED92D15CB33D136CBF717C14C3D`）和 `老婆的小营地-v0.9.30.apk`（当前重建 SHA-256 `F6202A888F095015673258BF03BCFFC79C4E5B4E79FCDBDC732D15418B8CCC62`）；旧候选归档到 `artifacts/releases/legacy/`。
- 仍未完成：TMDB/Trakt/豆瓣授权实时多源、来源原榜位次、长尾真实海报/完整剧情、排行榜 golden 像素、TalkBack/200% 字体、source push/tag/Latest Release；整体继续 `NOT_SIGNED_OFF`。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round45 - 2026-10-04 - 完整要求复盘与 v0.9.30 证据复核

- 本轮没有把来源榜的增量误报为“全行业全量完成”。最终资产仍是 200,509 条声明范围内快照；当前源分布为 IMDb-only 190,461、IMDb+TVmaze 10,026、TVmaze-only 22，另有 1 条无有效评分资料行；榜单仍不是 TMDB/Trakt/豆瓣等多站实时授权聚合，也没有来源原榜位次。
- 重新核对当前资产质量：无可靠中文展示名 122,494、无制作国家 71,312、无合法海报 188,167、缺完整剧情正文 189,715；MuMu 当前 IMDb 榜首仍能观察到英文标题和确定性占位，故中文优先/高保真素材门禁不能签收。
- 当前 v0.9.30/117 只在声明范围内通过了数据来源过滤、中文榜单切换、真实分页、构建/签名/对齐和 MuMu 进程稳定性；未完成排行榜 golden 像素、TalkBack/200% 字体、真实详情往返和全角色无条件通过。源代码未 push/tag，未创建或验证公开 Latest Release。
- 本轮结论：`NOT_SIGNED_OFF`。没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round46 - 2026-10-04 - 搜索稳定性、海报加载与 v0.9.31/118 本地候选

- 用户反馈“搜索以后非常卡、容易崩溃”和“很多剧加载不出海报”。根因复盘确认：追剧搜索缺少业务 deadline/串行门；海报全尺寸解码、无响应体/并发预算且禁用重定向；启动前 eager Cast SDK 可能把后台 TLS 异常写成下一次启动阻断。
- 修复：`DramaViewModel` 增加 30s 搜索 deadline、`Mutex` 单门和超时可重试错误；文本请求默认 8s connect/20s read；`DramaPoster` 增加安全 HTTPS 公网重定向（最多 3 跳）、8 MiB 上限、图片类型校验、720x960 采样、约 12 MiB 字节缓存和 3 并发槽；Cast 改为播放器惰性初始化；startup crash 只持久化 app-ready 前异常。新增 poster decode 结果回归，避免成功解码被错误丢弃。
- Android 版本升为 `0.9.31/118`。从 `T:\source` fresh `:app:testDebugUnitTest --offline --rerun-tasks` 为 `584/0/0/8 skipped`；Debug/Release assemble、aapt2、v2 单签名、zipalign 通过。Debug SHA `5003A2B4F7351EE0DF053A04DBFE949A7FD7D40AB126BCDE09AF975C660E05C9`，Release SHA `B7B5A30D005AA21C71D1487ABD781872CE06B66D8E1262BFD709FDF40FB1004D`。
- MuMu `emulator-5554` Android 12 fresh uninstall/install Release 后启动、追剧搜索 `test`、四轮滚动和 6 次快速重复搜索均通过；PID `660` 保持存活，搜索返回 24 条结果；首屏和滚动卡片可见真实海报；无 `FATAL EXCEPTION`/`OutOfMemory`/`ANR in`，crash buffer 为空；拉回安装 APK 与候选 Release SHA 一致。证据：`artifacts/candidates/20261004-search-poster-resilience-v0.9.31/`。
- 根目录严格只有 `老婆的小营地-debug-v0.9.31.apk` 与 `老婆的小营地-v0.9.31.apk`；v0.9.30 pair 已归档到 `artifacts/releases/legacy/20261004-v0.9.30-before-v0.9.31-search-poster/`。本候选未 push/tag/创建 public release；家庭 Wi-Fi 站内 WebView 播放和外站被拒绝/超大海报的长尾覆盖仍是现场限制。
- 本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round47 - 2026-10-04 - 中文标题优先、票数可信度排序与海报超时修复 v0.9.33/120

- 用户复盘发现排行榜仍展示大量英文。根因是目录已有中文身份但旧 UI 只消费 `displayTitle`，没有提取双语/中文别名；本轮在目录生成器与 Kotlin 展示层统一实现可验证中文侧优先。无可靠中文译名的条目继续保留原名，不臆造翻译；原始字段保留用于搜索和去重。
- 完整目录仍为 `200,509` 条，`catalogVersion=2026-10-04.4`、`identityMapVersion=...-v12`、methodology `imdb-rated-scope-v5-tvmaze-full-index`。默认排序仅在排序键上加入票数可信度权重，卡片仍显示公开评分，完整筛选和分页不被截断。
- MuMu 首轮新鲜验收暴露真实缺陷：远程排行榜海报在 `RankingPoster.kt:106` 的 `responseCode` 超时逃出 `runCatching`，造成 `FATAL EXCEPTION: main`。已改为可注入 loader，连接/响应/重定向/读取/解码/缓存全链路异常降级占位；新增 timeout regression。
- v0.9.33/120 重建后 Python `26/26`，Android JVM `587/0/0/8 skipped`；Debug/Release assemble、aapt2、v2 单签名和 zipalign 通过。根 APK Debug SHA `6C7CB3A7D506484A171580381F99B4A0CC61CC732CA37FD1409234D9F6AB1147`，Release SHA `C4B4CC4B95600BB68A152737FC9AAEFDC05B06CDBCCCAB0D71CE6E01B21DD4A1`。
- MuMu `emulator-5554` fresh uninstall/install/launch/排行榜/滚动后 PID `2701` 保持存活，榜首显示 `JOJO的奇妙冒险：钢球重跑`，快照显示 3065 条及可信度加权排序；修复后 app crash 过滤日志为空。修复前崩溃证据与修复后回归证据均保留在 `artifacts/candidates/20261004-cn-title-confidence-v0.9.33/`。
- 根目录只保留当前版本化 Debug/Release pair；修复前 v0.9.33 pair 归档到 `artifacts/releases/legacy/20261004-v0.9.33-pre-poster-crash-fix/`。本候选仍未 push/tag/创建 public release；未知中文身份、长尾海报、家庭 Wi-Fi provider/视频子资源和多站授权排行仍是明确限制。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## Round48 - 2026-10-05 - 全部年份语义与排行榜海报韧性 v0.9.34/121

- 用户截图复现确认：韩国+恐怖、年份“全部”只返回 2026 的 3 条；根因是 `period=year` 在 `BundledRankingRepository.matches()` 把 `year=null` 隐式替换为当前年。新增 `rankingTargetYear()`，null 保持全年份，显式年份仍精确过滤。
- 排行海报 loader 补齐浏览器兼容请求头、白名单 HTTPS 重定向、8 MiB 响应/720×960 采样/3 并发预算和异常降级；不改变排序/分页。空 Content-Type 的图片允许，HTML/未知主机/超大或无法解码响应回退确定性中文占位。这样《尸战朝鲜》《釜山行》等可达图片实际显示，Wikimedia 当前网络超时和无海报行仍诚实占位。
- 目录仍为 `catalogVersion=2026-10-04.4`、`itemCount=200,509`、fingerprint `86808722cfbf2e82`。MuMu Release 精确条件韩国+恐怖+全部年份显示 `234 条 · 1–50 条`，首屏年份 2019/2016/2023，截图中《尸战朝鲜》《釜山行》为真实海报；PID 4122 存活，清空 logcat 后无 FATAL。
- v0.9.34/121 根目录只保留版本化 Debug/Release pair，v0.9.33 归档于 `artifacts/releases/legacy/20261005-v0.9.33-before-v0.9.34-ranking-filter-poster/`；候选在 `artifacts/candidates/20261005-ranking-filter-poster-v0.9.34/`。Debug SHA `459F8E0602C4DBFD0B08001DA280F91E12CC6B4CE05830062BC77129C671199B`，Release SHA `27E46D700E5A69ED62B2CDE8659D3559776A152DF68DCFB437BBF08912E4B1F8`。
- 回归：Python `26/26`；Android JVM `589/0/0/8 skipped`；Debug/Release assemble、aapt2、apksigner v2、zipalign 通过。SA/UX/QA/QC-数据/QC-回归/QC-视觉证据分开列于 release record 与 candidate README。本候选未 source push/tag/Latest Release，仍不称为 public release；全量合法海报、授权多站实时排行、golden 像素/无障碍仍未签收。
- 本项目没有写入任何 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-18 Round53 发布通道复核

- 本地提交 `c368cab` 和 annotated tag `v0.9.38+125` 已生成。
- `git push origin main` 与 tag 推送被当前环境阻断：全局 URL 重写到 `kkgithub.com` 后 TLS 报 `SEC_E_WRONG_PRINCIPAL`；关闭校验后镜像不支持 DAV locking；直连 GitHub 443 不可达。
- 因此本轮只确认本地候选与本地 tag，未声称源码已远端可见、Latest Release 已创建或 updater 公网资产可下载。

## Round54 - 2026-10-18 - 排行榜终端文案清理与全量深测

- 移除排行榜中面向内部验收/数据审计的来源覆盖、资料待补、快照、方法、原榜位、数据时间和“排名分/综合参考分”展示；卡片保留数字评分 tag、中文标题/元数据、简介、`评分 x/10` 与“去搜索”。
- 设置页移除“高保真视觉验收”入口；错误、加载、刷新和重试状态保留为用户可执行提示。无障碍评分语义同步改为“评分”。
- 版本仍为 `0.9.38/125`；根目录仅有当前 Debug/Release pair，SHA 分别为 `F4192C4CB7A610A7312A469BA3216BDA7348A08294B3BD4AAB6F8804F9E946A8`、`1E73D44D9D8EB02512104CB1A2A48E64D27502802653C7B75DDBB4A82A21DC9`。
- 验证：Python `35/35`；Android JVM `604/0/0/8 skipped`；Debug/Release assemble、aapt2、Release apksigner v2 通过；MuMu `emulator-5554` fresh Release 完成书架/追剧/排行榜/筛选/刷新/搜索桥接/返回/有声/设置/更新检查，PID `3580` 存活，无 FATAL/OOM/ANR。证据见 `artifacts/candidates/20261018-ranking-copy-depth-v0.9.38/`。
- 未关闭：无合法海报 URL 的条目继续确定性占位；当前数据仍为 IMDb+TVmaze 随包快照；本轮未 source push/tag/Latest Release，不称为已发布。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。

## 2026-10-18 Round55 - 播放器空白超时与片源回退 v0.9.40/127

- 根因：`m.88ystv.com` 是 `www.88ystv.com` 的站内别名，旧 ready 判断把它当外部 provider；iframe/资源完成回调在没有可见媒体时提前宣告 ready，导致《九门》首源空白且无超时操作。直接顶层导航 provider 在 MuMu 返回 403，因此保留站内 iframe 策略。
- 修复：canonical host 比较；playerReady 仅由真实外部 provider 完成或 inline media 确认；optional 资源错误不阻断；新增同集片源回退与超时“切换片源”按钮。MuMu 全局代理 `10.0.2.2:7897` 已清除并重启，不能将该测试环境问题写成代码根因。
- 版本/构件：Android `0.9.40/127`；根目录严格只保留 `老婆的小营地-debug-v0.9.40.apk` SHA `03AC5D18907FE77893B83CBAAA1B76A5C710BD173D22A498488AB9621CB34A81` 与 `老婆的小营地-v0.9.40.apk` SHA `3AC6BDC06B4E9EB14227EA4D6E9066339F521B0F1DE075C5E50302E4E5E144EC`；v0.9.39 已移至 legacy。
- 验证：ASCII 临时副本 `C:\CodexTemp\lovely-camp-test` Android JVM `607/0/0/8 skipped`；Python `35/35`；Debug/Release assemble、v2 单签名、zipalign 通过；MuMu 首源 15 秒超时卡片→切换片源→红牛资源第 1 集真实画面、暂停和 `00:07/39:10` 通过，无 FATAL/OOM/ANR。随后卸载→重新安装→显式启动→追剧首页→排行榜冒烟，排行榜 XML 未发现内部验收词。证据 `artifacts/candidates/20261018-player-resilience-v0.9.40/`。
- 仍未关闭：家庭 Wi‑Fi 物理设备 WebView 子资源现场复测、source push/tag、GitHub Latest Release/public updater verification；本轮为本地验证候选，不称为公开发布。本项目没有写入 worklog、timesheet、ZenTao 或工时报告。
