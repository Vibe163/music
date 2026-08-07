# 变更记录（opencode 会话 · 2026-08-07）

> 本次会话为项目做了「使用体验 + 观感 + 健壮性」三方面的增强。
> 所有改动均通过 `gradlew assembleRelease`（JDK 17）完整构建验证。

## 功能新增

### 播放器核心（`PlayerController.kt` / `PlayerViewModel.kt`）
- **倍速播放**：`setPlaybackSpeed` / `getPlaybackSpeed`，速度范围 0.25x–3.0x 限制，
  `PlayerUiState.playbackSpeed` 跟踪当前倍速，`EVENT_PLAYBACK_PARAMETERS_CHANGED` 同步状态。
- **播放队列管理**：
  - `removeFromQueue(songId)`：从队列移除单曲（删空自动停播），不删曲库数据。
  - `clearQueue()`：清空整个队列并停止播放。
  - `playNextInQueue(song)`：把歌曲插入当前曲目之后，不打断当前播放。
- **单曲失败自动跳过**：`onPlayerError` 改为「有下一首就自动切歌」，仅当队列无其他歌曲时才停止
  并清空队列，替代之前错误即清空整队的逻辑（健壮性提升）。
- `buildMediaItem(song)` 抽取 MediaItem 构造，三处复用。

### 2. 播放页 UI（`NowPlayingScreen.kt`）
- 新增**倍速控制**：底部工具栏「倍速」按钮 + `SpeedSheet`（0.5x–2.0x 选择面板）。
- 播放队列面板（`QueueSheet`）：
  - 每首歌右侧新增**移除按钮**（移除该曲）。
  - 顶部新增**清空队列**。
  - 增加操作引导文案。

### 3. 歌单管理（`PlaylistDetailScreen.kt` / `Dialogs.kt`）
- 歌单详情页右上角菜单：**重命名歌单 / 删除歌单**（用户自建歌单，主收藏不可用）。
- 新增 `RenamePlaylistDialog`。

### 4. 歌曲菜单增强
- 曲库 / 歌单详情歌曲菜单新增「播放」「下一首播放」入口。

### 5. 迷你播放器（`MiniPlayerBar.kt`）
- 新增**上一首**按钮（`previousOrRestart`：回到开头 / 上一曲）。
- 封面在播放时**缓慢旋转**（唱片机质感），暂停即静止。

### 6. 视觉打磨（`NowPlayingScreen.kt` `CoverWithSwipe`）
- 播放页封面由「圆角方形旋转 → **黑胶唱片风格**」：唱盘底盘 + 居中圆封面 + 唱片轴心
  中心孔 + 顶部高光弧，旋转/拖动手感保留。

## 预期效果
- 播放器更主用：倍速追剧听书、单曲失败不再「整队暴死」。
- 队列成可管理资源：移除/清空/插到下一首。
- 歌单可改名可删除（原只可建不可改）。
- 迷你播放器与播放页观感更精致。

## 构建环境备注
- 机器默认 JDK 25 与 Kotlin 2.0.21 不兼容（`IllegalArgumentException: 25.0.2`）。
- 构建使用独立 JDK 17（`C:\Users\Administrator\AppData\Local\Temp\opencode\jdk17\jdk-17.0.20+8`），
  未改动项目任何构建配置。