# Project Memory

## Current candidate refresh: 2026-08-25 Version 0.8.23+86 - home Wi-Fi fallback and startup update hardening

- Git delivery: implementation and CI hardening are now on GitHub `main` at remote commit `f321039a6877757cb559aeda3260a03326b0fda9` (the source-side equivalent is local commit `1c9aab1`). The matching annotated tag `v0.8.23+86` completed Actions run `32797619685` successfully.
- Root artifacts are the single current pair: [Debug `老婆的小营地-debug-v0.8.23.apk`](../老婆的小营地-debug-v0.8.23.apk), SHA-256 `D50FCBF35AE30BEA4793A1E8520CD5D843E5070A006B90C8D80C8E964AC31ABC`, 48,379,656 bytes; [Release `老婆的小营地-v0.8.23.apk`](../老婆的小营地-v0.8.23.apk), SHA-256 `6B45934764C36094C23C1920FB3C17E50F21405783DBB0E9FB326FB2AF9ABB3F`, 37,073,967 bytes. Previous v0.8.22 packages were moved to `artifacts/releases/legacy/` before replacement. Source `app/build/outputs/apk/{debug,release}` was synchronized to these same hashes.
- Network fix: the supplied home-Wi-Fi log is confirmed DNS poisoning/incorrect DNS (`::1` loopback plus `221.228.32.13` timeout versus public CDN `23.224.113.227/.228/.229`). v0.8.23 keeps HTTPS AliDNS, dynamic A records, original Host/SNI/certificate checks, but generates an internal trusted-address transport URL before Android URL-stack hostname resolution; the old `SSLSocketFactory`-only interception could be too late. Fallback remains video-only, in-memory and bounded; no global DNS change, user-visible bare IP or TLS weakening.
- Startup update: `AndroidAppUpdater.checkAutomatically()` now accepts any `NET_CAPABILITY_VALIDATED` network, including mobile data, once per 24 hours. A newer GitHub Release maps to the startup prompt; download progress reports bytes/percent/speed and verification, while download/install remain user-confirmed.
- Public update delivery: GitHub Latest is now `v0.8.23+86`, asset `lovely-camp-v0.8.23.apk`, 33,947,113 bytes, digest `sha256:57b83818278016452134adc99ae68f0589f5c4e5da64dbc8750941e95cd5de67`. The downloaded asset matched that digest, is `com.lovelyreader` versionCode `86`/versionName `0.8.23`, APK Signature Scheme v2 with one signer, and passed `zipalign -c`.
- Release CI hardening: machine-specific `org.gradle.java.home`/temporary-directory properties were removed; the external splash-material compression task now treats its parent-workspace inputs as optional and safely skips them in a clean checkout. The GitHub Actions signing secrets were provisioned out-of-band and are not stored in source or logs.
- Verification: ASCII copy `C:\CodexTemp\home-wifi-update-20250825-v3` passed full `testDebugUnitTest` with 313 tests, 0 failures, 0 errors, 8 skipped; `assembleDebug assembleRelease` passed (84 actionable tasks). Release is package `com.lovelyreader`, versionCode `86`, versionName `0.8.23`; `apksigner` v2=true/1 signer; `zipalign -c` passed. Final rebuilt Debug/Release cold start, settings, drama home/search, diagnostics and Release nine-page concept gate are in `docs/ui/evidence/20260825-high-fidelity/iteration43/`; no FATAL/ANR markers.
- Pixel gate: all 9 approved concept assets have identical SHA-256 in `docs/ui/concepts-20260823-9x16/` and `source/app/src/main/assets/concept/`; current Release screenshots map each content area to its asset with MAE `0.227–0.709` after the page-specific crop policy. This is a static concept-gate result, not a claim that arbitrary dynamic production data is mathematically identical.
- Sign-off records: `docs/ui/team-audit/ux-review-final43-20260825.md`, `qa-review-final43-20260825.md`, and `qc-crosscheck-final43-20260825.md`. Live limitations remain: affected physical home Wi-Fi must still show “可信 DNS 回退成功” and real search/playback; update asset download and digest verification are now live-verified, while Android installer confirmation, real media, casting, background downloads and HarmonyOS remain external acceptance items.
- Workspace hygiene: temporary captures remain under dated `../docs/ui/evidence/` folders; the nine legacy `diagnose_*.py` utilities are under `../tools/diagnostics/legacy/`, leaving only delivery APKs and project entry files in the workspace root.

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
