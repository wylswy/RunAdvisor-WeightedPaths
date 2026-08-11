# 让 CI 跑起来（约 10 分钟）

CI（GitHub Actions）会在每次 push 时跑 `mvn test`（当前 204 个测试）。测试需要游戏本体 `desktop-1.0.jar`（约 352MB），但该 jar 是商业游戏文件、不能进公开仓库，所以需要你把它放到一个 CI 能下载到的地方。

推荐用 **GitHub 私有仓库 Release 附件**，全程在 GitHub 网页上完成，不依赖任何第三方网盘。

## 第一步：建一个私有"素材"仓库

1. 打开 https://github.com/new
2. Repository name 填 `RunAdvisorAssets`
3. **选 Private**（私有）
4. Create repository

## 第二步：把游戏 jar 传上去

1. 进入 `RunAdvisorAssets` 仓库 → 右侧 **Releases** → **Create a new release**
2. Tag 填 `v1`
3. 拖拽上传 `desktop-1.0.jar`（GitHub 单附件上限 2GB，352MB 没问题）
4. **这个 release 里只放这一个附件**（工作流默认取第一个附件）
5. Publish release

## 第三步：建一个只读令牌（PAT）

1. 打开 https://github.com/settings/tokens?type=beta （fine-grained tokens）
2. **Generate new token**
3. Token name 随意（如 `run-advisor-ci`）
4. **Repository access** 选 **Only select repositories** → 勾选 `RunAdvisorAssets`
5. **Permissions** → Repository permissions → **Contents** 选 **Read-only**（其他保持 None）
6. Generate token，**立刻复制**（只显示一次）

## 第四步：在主仓库加两个 secret

1. 打开你的主仓库：https://github.com/wylswy/RunAdvisor-WeightedPaths/settings/secrets/actions
2. **New repository secret** 加：
   - `ASSETS_REPO` = `wylswy/RunAdvisorAssets`
   - `GH_ASSETS_TOKEN` = 第三步复制的 PAT
3. 保存

## 第五步：验证

推送任意提交（或去 Actions 页手动 Re-run），应看到 `mvn test` 任务开始跑，204 个测试全绿即配置成功。

## 备选方案：任意私有直链

如果你有阿里云 OSS / 腾讯云 COS / 私有服务器，也可以只配一个 secret：
- `STS_GAME_JAR_URL` = 指向 `desktop-1.0.jar` 的私有直链 URL

工作流会自动识别：有 `GH_ASSETS_TOKEN + ASSETS_REPO` 走 GitHub 附件，否则有 `STS_GAME_JAR_URL` 走直链，都没有则跳过测试任务（不报错）。

## 本地开发不需要这些

本地跑测试不受影响：`scripts/setup-libs.ps1` 把游戏 jar 从你的游戏安装目录复制到 `lib/` 后，`mvn test` 照常全绿。CI 只是给"机器门禁"用的。