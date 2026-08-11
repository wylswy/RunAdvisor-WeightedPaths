# lib/ 依赖目录

| 文件 | 来源 | 是否进仓 |
|------|------|---------|
| `ModTheSpire.jar` | [kiooeht/ModTheSpire](https://github.com/kiooeht/ModTheSpire/releases)（开源，随仓） | ✅ |
| `BaseMod.jar` | [daviscook477/BaseMod](https://github.com/daviscook477/BaseMod/releases)（开源，随仓） | ✅ |
| `desktop-1.0.jar` | 杀戮尖塔游戏本体（**版权原因不进仓**） | ❌（gitignored） |

游戏本体 jar 的获取方式：

```powershell
# 方式一：指定游戏安装目录（Steam 默认路径）
.\scripts\setup-libs.ps1 -GameDir "C:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire"

# 方式二：直接指定 jar 路径
.\scripts\setup-libs.ps1 -GameJar "D:\games\desktop-1.0.jar"
```

构建前请确保 `lib/desktop-1.0.jar` 存在；缺失时 Maven 会报清晰的错误提示。