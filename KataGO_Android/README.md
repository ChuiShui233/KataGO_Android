# KataGo Android

一个使用 Jetpack Compose + Fluent UI 编写的 Android 围棋客户端，内置 KataGo 引擎，支持本地 CPU/GPU 对弈与多厂商 AI 棋局解说。

## 功能

- **人机对弈**：选择棋盘大小（9/13/19）与执棋颜色，支持让子、虚手、悔棋、终局数子。
- **双引擎**：
  - CPU 引擎 (`libkatago.so`)：作为独立进程 exec() 启动，通过 GTP PIPE 通信。
  - GPU 引擎 (`libkatago_opencl.so`)：编译为 shared library，通过 JNI 加载到 App 进程内（避免 linker namespace 限制），同样通过 GTP PIPE 通信。
- **GPU (clvk)**：通过 Vulkan 运行 OpenCL，需要导入配套编译的 `libOpenCL.so`（clvk）；未导入时禁止使用并自动回退 CPU。
- **AI 解说与分析**：支持 Groq / Gemini / OpenAI / Claude / DeepSeek / Kimi / Qwen / OpenRouter 等多家 LLM 提供商，自动分析人类失误、实时胜率/目差、对话式提问。
- **失误自动检测**：根据 KataGo 胜率变化自动判断重大失误/一般失误，仅在必要时调用 AI，避免浪费 Token。
- **请求队列与优先级**：用户主动请求 > 重大失误 > 转折点 > 一般失误 > 战斗 > 好棋，队列自动丢弃过时请求。
- **多提供商故障转移**：一个提供商失败时自动回退到下一个，支持手动/自动/轮询/最快/最便宜等路由策略。
- **API Key 安全存储**：使用 Android Keystore + AES-GCM 加密，永不以明文存储。
- **Token 用量统计**：按日/月统计请求数、Token 消耗和估算费用。
- **棋盘交互**：落子采用"点击预览 → 再次点击确认"防止误触；可开关 AI 思考时计划落子位置标记；坐标标签（A~T / 数字）。
- **对局管理**：保存/读取对局、加载模型文件、终局弹窗显示胜负/平局与目差。
- **引擎容错**：启动失败自动重试一次，仍失败再返回主页并显示诊断信息。
- **多语言**：中文（简/繁）、英文、日文、韩文、德文。

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Fluent UI |
| 异步 | Kotlin Coroutines + Flow |
| 网络 | HttpURLConnection |
| 持久化 | SharedPreferences + Android Keystore |
| 引擎 | KataGo 1.17.2（JNI native） |

## 项目结构

```
kataA/
├── app/src/main/kotlin/com/chuishui/katago/
│   ├── MainActivity.kt              # 主入口，导航与棋盘 UI
│   ├── BoardView.kt                 # 棋盘渲染
│   ├── SettingsScreen.kt            # 设置页面
│   │
│   ├── engine/                      # KataGo 引擎层
│   │   ├── KataNative.kt            # JNI native 方法声明
│   │   ├── GtpEngine.kt            # GTP 协议封装
│   │   ├── ModelStore.kt           # 模型文件管理
│   │   ├── NetworkStore.kt         # 网络配置
│   │   └── AutotuneWatchdog.kt     # GPU 自动调优看门狗
│   │
│   ├── goai/                        # 棋局 AI 分析核心
│   │   ├── GoAiCoach.kt            # AI 教练接口
│   │   ├── GoAiCoachImpl.kt        # AI 教练实现（Prompt 构建 + 调用）
│   │   ├── GoAiEvent.kt            # 事件模型（GoAiEvent / GoAiContext / GameAnalysis）
│   │   ├── GoMistakeDetector.kt    # 失误检测器（胜率阈值判断）
│   │   └── GoAiPromptBuilder.kt    # Prompt 构建器
│   │
│   ├── ai/                          # AI 提供商抽象层
│   │   ├── AiContainer.kt          # DI 容器
│   │   ├── ProviderFactory.kt      # 提供商工厂
│   │   ├── model/
│   │   │   ├── AiRequest.kt        # 统一请求模型
│   │   │   ├── AiResponse.kt       # 统一响应模型
│   │   │   ├── AiCapabilities.kt   # 提供商能力描述
│   │   │   └── ResponseFormat.kt   # 响应格式
│   │   ├── provider/
│   │   │   ├── AiProvider.kt       # 提供商接口
│   │   │   ├── AiHttpClient.kt     # HTTP 客户端抽象
│   │   │   ├── AiException.kt      # 统一异常类型
│   │   │   ├── ProviderHealth.kt   # 健康状态
│   │   │   ├── openai/             # OpenAI 兼容系列
│   │   │   │   ├── OpenAiCompatibleProvider.kt
│   │   │   │   ├── GroqProvider.kt
│   │   │   │   ├── OpenAiProvider.kt
│   │   │   │   ├── DeepSeekProvider.kt
│   │   │   │   ├── QwenProvider.kt
│   │   │   │   ├── KimiProvider.kt
│   │   │   │   └── OpenRouterProvider.kt
│   │   │   ├── gemini/
│   │   │   │   └── GeminiProvider.kt
│   │   │   └── anthropic/
│   │   │       └── ClaudeProvider.kt
│   │   ├── router/
│   │   │   ├── AiRouter.kt         # 路由与故障转移
│   │   │   └── AiRoutePolicy.kt    # 路由策略枚举
│   │   ├── queue/
│   │   │   └── AiRequestQueue.kt   # 优先级请求队列
│   │   ├── security/
│   │   │   └── AiApiKeyStore.kt    # Keystore 加密存储
│   │   ├── usage/
│   │   │   ├── AiUsageManager.kt   # Token 用量追踪
│   │   │   └── AiCostTable.kt      # 费用估算表
│   │   ├── config/
│   │   │   ├── AiProviderConfig.kt # 单提供商配置
│   │   │   ├── AiConfigStore.kt    # 配置持久化
│   │   │   ├── AiConfigSource.kt   # 配置读取接口
│   │   │   └── AiGlobalSettings.kt # 全局 AI 设置
│   │   ├── registry/
│   │   │   └── AiProviderRegistry.kt # 提供商注册表
│   │   └── log/
│   │       └── AiLogger.kt         # AI 日志
│   │
│   ├── config/                      # 应用配置
│   │   ├── PersonalizationSettings.kt
│   │   └── ConfigExport.kt
│   ├── save/
│   │   └── GameSaveStore.kt        # 对局存档
│   └── game/
│       ├── GameSetup.kt            # 对局配置
│       └── AiMoveOutcome.kt        # AI 落子结果
│
├── app/src/main/jniLibs/arm64-v8a/  # native 库
├── KataGo-1.17.2/                   # KataGo 引擎源码
├── build-tools/                     # 交叉编译脚本
├── build.sh                         # App 构建脚本
└── gradle/                          # Gradle 配置
```

## 构建要求

- Android SDK：compileSdk 35，minSdk 29，targetSdk 35
- Android NDK（仅编译引擎需要）
- Gradle 8.11.1（或可用的 Gradle 发行版）
- JDK 11

## 构建 App

```bash
./build.sh assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> `build.sh` 使用本机路径的 Gradle 发行版，请按你的环境调整。

发布版签名（可选），通过 Gradle 属性提供，否则不启用签名：

```properties
KATA_ANDROID_KEYSTORE=/path/to/keystore.jks
KATA_ANDROID_KEYSTORE_PASSWORD=...
KATA_ANDROID_KEY_ALIAS=...
KATA_ANDROID_KEY_PASSWORD=...
```

## 模型

KataGo 权重模型存放于设备存储：

```
/storage/emulated/0/Download/KataGO-AOS/model/
```

支持从本地目录或网络直接导入 `.bin.gz` / `.txt.gz` 权重文件。

## 编译引擎 (Android)

编译 GPU 引擎：

```bash
bash build-tools/build-android-opencl.sh
```

脚本会基于本仓库的 `KataGo-1.17.2` 源码与 `android-toolchain.cmake` 生成 `libkatago_opencl.so`，产物需放置到 `app/src/main/jniLibs/arm64-v8a/`。

## GPU 引擎与 clvk

GPU 对局需要 `libOpenCL.so`（clvk，经由 Vulkan 运行 OpenCL）。请使用我们编译的 clvk——为适配 KataGo 修改了官方源码。可通过应用内链接下载或者从我的服务器下载并导入，或点击"选择文件导入"导入 `.zip` / `.so` 文件。导入成功后 GPU 对局走 Vulkan；否则自动退回 CPU。

## AI 解说架构

```
KataGo Engine
     ↓
GoMistakeDetector  ←  胜率阈值判断
     ↓
GoAiEvent
     ↓
GoAiCoach  →  GoAiPromptBuilder
     ↓
AiRouter
     ↓
AiRequestQueue  →  优先级队列，丢弃过时请求
     ↓
AiProvider
     ↓
AiCommentaryResult → UI
```

### 失误检测

`GoMistakeDetector` 根据 KataGo 报告的胜率变化决定是否触发 AI 分析：

| 模式 | 阈值 | 行为 |
|------|------|------|
| OFF | — | 从不自动调用 |
| MAJOR_MISTAKES | ≥ 10% | 仅重大失误 |
| IMPORTANT_MISTAKES | ≥ 5% | 一般失误 + 重大失误 |
| AGGRESSIVE | ≥ 3% | 几乎每步分析 |

### 路由策略

事件类型决定路由策略，不是全局统一选择：

| 事件 | 策略 | 说明 |
|------|------|------|
| 重大失误 (BLUNDER) | BEST | 用质量最好的提供商 |
| 一般失误 / 转折点 / 战斗 | AUTO | 按优先级排序，失败自动回退 |
| 好棋 (GOOD_MOVE) | CHEAPEST | 选最便宜的（节省 Token） |
| 用户提问 | MANUAL | 用户手动指定的提供商 |
| 棋局总结 | AUTO | 同上 |

`AUTO` 按提供商优先级排序，不考虑费用。`AiUsageManager` 仅做统计，不参与路由。

### Token 节省

- 仅在胜率损失超过阈值时才调用 AI
- 使用紧凑 Prompt，不发送完整棋盘/SGF
- 优先级队列自动丢弃过时请求
- 一盘棋约 5-15 次 AI 请求

### AI 两种模式

App 内置两种 AI 交互模式，通过开关切换：

**解说模式**（默认，底部紧凑面板）
- 基于 KataGo 数据（胜率、最佳着、PV、目差）让模型解释
- 模型不自行计算，只用人话解释 KataGo 的结论
- 调用 `GoAiCoach.answer()`，发送紧凑上下文
- 自动触发：失误检测器发现失误时自动弹出

**指导模式**（全屏对话，右滑进入）
- 发送完整棋盘 JSON，模型**不依赖 KataGo 数据**，完全自主推理
- 调用 `GoAiCoach.answerGlobal()`，参数更宽松：
  - `maxTokens = 6000`（不限制推理长度）
  - `reasoningEffort = "high"`
  - `timeout = 120s`
- 用户可自由提问，模型可推荐具体着法
- 推荐着法用 `[tip]D4[/tip]` 标记，棋盘上自动高亮
- 支持多轮对话，保留上下文
- Debug 包可查看原始请求/响应

两种模式的 Prompt 区别：
- 解说模式："KataGo 说 R14 导致胜率从 72% 降到 64%，解释为什么"
- 指导模式："这是当前棋盘，你自己判断，推荐下一手"

### API Key 安全

- 使用 Android Keystore 存储 AES-256 主密钥
- API Key 加密后存储为 `iv(12) + ciphertext` 的 Base64 字符串
- 永不以明文写入源码、Git、BuildConfig 或 SharedPreferences

## AI 解说配置

在"设置 → AI助手"中：

1. 选择提供商（Groq / OpenAI / Gemini / ...）
2. 填写 Base URL
3. 填写 API Key
4. 选择模型
5. 测试连接

支持失败自动回退到其他已配置的提供商。

## 许可

- 应用代码遵循仓库内声明的许可。
- KataGo 引擎源码位于 `KataGo-1.17.2/`，遵循 KataGo 自身许可（见该目录 LICENSE）。
- clvk 为第三方库，经修改后随应用分发，遵循其原始许可。
