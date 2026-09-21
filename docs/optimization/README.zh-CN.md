# Create: Biotech 仓库优化审计

本目录记录对 Create: Biotech 仓库的持续优化审计。审计采用“完成一个模块、立即落一份文档”的方式推进；未核验的判断不会写成确定结论。

## 审计目标

- 覆盖构建与依赖、Java 架构、运行时性能、资源与数据生成、兼容层与 Mixin、测试与发布流程。
- 优先使用仓库内 `ref/` 参考源码核对 Create、JEI、Forge 及相关 API 行为。
- 每条建议尽量包含证据、影响、优先级、实施方向和验证方式。
- 区分“缺陷修复”“风险控制”“维护性改进”和“可选重构”，避免把风格偏好伪装成必要改动。

## 优先级定义

| 等级 | 含义 |
| --- | --- |
| P0 | 已知会阻断构建、发布、核心玩法或造成严重数据/兼容问题，应立即处理 |
| P1 | 高概率引发运行时故障、版本兼容回归或显著维护成本，应进入近期计划 |
| P2 | 能明显改善性能、可测试性、可维护性或开发体验，适合分阶段实施 |
| P3 | 收益较小或偏长期治理，可随相关功能改动顺带完成 |

## 当前进度

| 模块 | 状态 | 文档 |
| --- | --- | --- |
| 仓库基线与风险地图 | 已完成 | [00-repository-baseline.zh-CN.md](00-repository-baseline.zh-CN.md) |
| 构建、依赖、映射与发布 | 已完成 | [01-build-dependencies-release.zh-CN.md](01-build-dependencies-release.zh-CN.md) |
| Java 架构与逻辑边界 | 已完成 | [02-java-architecture-network.zh-CN.md](02-java-architecture-network.zh-CN.md) |
| 性能与运行时热点 | 已完成 | [05-runtime-performance.zh-CN.md](05-runtime-performance.zh-CN.md) |
| 资源、数据生成与本地化 | 已完成 | [04-resources-datagen-localization.zh-CN.md](04-resources-datagen-localization.zh-CN.md) |
| 兼容层与 Mixin | 已完成 | [03-mixin-compatibility.zh-CN.md](03-mixin-compatibility.zh-CN.md) |
| 测试、诊断与开发者体验 | 已完成 | [06-testing-diagnostics-developer-experience.zh-CN.md](06-testing-diagnostics-developer-experience.zh-CN.md) |
| 综合路线图 | 已完成 | [07-priority-roadmap.zh-CN.md](07-priority-roadmap.zh-CN.md) |
| 史莱姆传送带 Create 兼容差异与修复方案 | 已完成 | [09-slime-belt-create-parity-audit-and-fix-plan.zh-CN.md](09-slime-belt-create-parity-audit-and-fix-plan.zh-CN.md) |
| 新手术台系统性能审计与优化 | 已完成 | [11-surgical-table-performance.zh-CN.md](11-surgical-table-performance.zh-CN.md) |
| 配方数据驱动审计与 P0 实施方案 | P0 已完成 | [12-recipe-data-driven-audit-and-p0-plan.zh-CN.md](12-recipe-data-driven-audit-and-p0-plan.zh-CN.md) |

## 审计基准

- 基准提交：`511533e2ef52c25dfa6f65bab5d19ebd49e063c2`
- 审计日期：2026-07-14
- 审计开始时工作区状态：无未提交改动；`main` 相对 `origin/main` 领先 11 个提交
- 目标环境：Minecraft 1.20.1、Forge 47.1.33、Java 17、Create 6.0.8-291

后续若代码继续变化，各章节会注明其核验提交或重新扫描日期。
