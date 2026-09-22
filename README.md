# audiobook-player

一个有意思的**离线**有声书 App · An offline audiobook player for Android.

> 这是一个以**学习软件开发全流程**为目标的敏捷项目：从需求讨论、UI 设计、代码开发到打包部署，全过程在本仓库存档。

## 愿景

为本地有声书收藏者打造一个纯粹的离线收听工具：把音频文件夹变成一个有进度记忆的书架，随时随地接着上次听。

- **一个文件夹 = 一本书**，文件夹内一个音频文件 = 一章
- 每本书独立记住收听进度（章节 + 章内位置 + 上次收听时间）
- 无账号、无服务器、纯离线

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) |
| 播放引擎 | Media3 / ExoPlayer |
| 本地数据库 | Room |
| 架构 | MVVM + Repository，单 Activity + Compose Navigation |
| 最低系统 | Android 8.0 (API 26) |

## 参考项目

- [Voice](https://github.com/PaulWoitaschek/PaulWoitaschek/Voice) —— 开源极简有声书播放器的标杆，本项目架构的重要研读对象

## 文档

- [MVP 产品需求文档 (PRD v0.1)](docs/PRD-v0.1-MVP.md)

## 开发流程（敏捷）

- 需求 = GitHub Issues（用户故事格式）
- 迭代 = Sprint（约 2 周一轮）
- 每个功能一个分支 → Pull Request → Code Review → 合并

## 当前状态

🏗️ Sprint 1 筹备中 —— 见 Issues 列表
