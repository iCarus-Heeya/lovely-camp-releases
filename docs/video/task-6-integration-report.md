# Task 6: Drama app integration

## Result

The existing reader remains the default experience. A persistent, top-level `小书架 | 追剧` switch now changes between the existing reader navigation and the drama flow without replacing or resetting the reader `LibraryViewModel`.

The drama side is wired to real injected services in `LovelyReaderApp`:

- `AndroidVideoPageFetcher` provides network HTML to both the root resolver and site adapter.
- `VideoSiteResolver` uses `AndroidVideoRootStore` for the last validated public root.
- `DefaultVideoSiteAdapter` supplies search, title, source, episode, and media data.
- `VideoLibraryRepository` persists drama metadata through `AndroidVideoPersistence`.
- `VideoDownloadCoordinator` uses `AndroidDownloadManagerGateway`, which enqueues eligible HTTPS MP4 files in Android's `DownloadManager` and stores them under the app's external Movies directory.

No sample titles, episodes, or media URLs were added. Empty and unavailable states continue to reflect resolver/adapter results.

## Playback

Episode rows now have an explicit `播放` action. `DramaViewModel.openEpisode` resolves the selected episode's `VideoMediaLink`; encrypted or missing links remain unavailable. A Media3 `ExoPlayer`/`PlayerView` screen plays the resolved URL, exposes the standard Media3 controller, and releases the player when leaving the screen. The Media3 HLS module was added so ordinary HLS links can be played as well as formats supported by the existing ExoPlayer dependency.

## Android integration

No manifest or provider change was necessary. The manifest already declares Internet and notification permissions, and `DownloadManager.Request.setDestinationInExternalFilesDir` does not require a custom `FileProvider` or broad storage permission.

## Verification

A view-model test was added for resolving a real selected episode media link into playback state. The requested Gradle test run was attempted once, but Android Gradle Plugin stopped before compilation because the workspace path contains non-ASCII characters. Per the task constraint, the build was not retried. The exact blocker was the Android plugin path check (`Your project path contains non-ASCII characters`).
