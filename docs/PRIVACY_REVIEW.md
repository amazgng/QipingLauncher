# 隐私检查 / Privacy review

检查日期 / Reviewed: 2026-10-23

## 中文

本次检查覆盖上传的源码、配置、测试、README、历史验证记录、交接文档（含 Word 的正文与元数据）、图片及 Git 提交作者邮箱。

- 已匿名化交接资料中的个人电视型号、购买时间及实际设备读数，移除手机截图参数与个人设备的关联。
- 已将构建记录的内部工作目录和网络示例地址替换为占位符；清理 Word 作者、修订会话标识和内嵌缩略图。
- 未发现真实邮箱、手机号、访问令牌、密码、私钥或签名密钥文件。测试中的示例邮箱、运行时生成的配对令牌和图片方向测试元数据保留；它们不是个人凭据。
- 图片检查未发现个人照片或可识别的个人资料。保留通用壁纸、应用图标与模拟器截图。
- 应用源码、资源、构建逻辑和版本未改动。包名、GitHub 账号署名、通用厂商兼容性说明与模拟器测试参数保留。
- 本次只做隐私检查与文档校验，没有重新构建应用或执行设备功能回归；不能据此保证不存在任何形式的隐私信息。

交接目录的 `SHA256SUMS.txt` 对应当前匿名化后的文件，不再列出未包含在仓库中的原始源码压缩包。历史 `source_fingerprints.json` / `package_files.json` 仍记录当时验证的原始内容；已匿名化文档与其历史摘要可能不一致，不应将其视作当前仓库清单。

主分支历史使用清理后的导入版本替换此前两次上传提交。重写分支历史不等于从 GitHub 服务端彻底销毁旧 Git 对象，也无法撤回他人已有的副本；本次不声称已完成服务端永久清除。GitHub 账号名称仍用于仓库归属和提交署名，提交邮箱为 GitHub `noreply` 隐私邮箱。

## English

The review covered uploaded source, configuration, tests, READMEs, historical validation records, handoff documents (including Word content and metadata), images, and Git commit email addresses.

- Anonymized personal TV model, purchase date and device readings, and removed the link between a screenshot's dimensions and a personal phone.
- Replaced internal build paths and a network example address with placeholders; removed Word authorship metadata, revision-session identifiers and the embedded thumbnail.
- Found no actual personal email addresses, phone numbers, access tokens, passwords, private keys or signing-key files. Synthetic test email addresses, runtime-generated pairing tokens and image-orientation test metadata remain; these are not personal credentials.
- Image inspection found no personal photos or identifiable personal information. Generic wallpapers, icons and emulator screenshots remain.
- Application source, assets, build logic and version are unchanged. The package name, GitHub account attribution, generic vendor compatibility notes and emulator test parameters remain.
- This was a privacy and documentation review, without a new application build or device regression run. It does not guarantee the absence of every possible form of private information.

The handoff directory's `SHA256SUMS.txt` now covers its sanitized files and omits the original source archive, which is not included in the repository. Historical `source_fingerprints.json` / `package_files.json` files still describe the originals tested at the time. Sanitized documents may differ from those historical digests; these files are not manifests of the current repository.

The cleaned import replaces the two earlier upload commits in the main branch history. Rewriting a branch does not permanently erase old Git objects from GitHub or recall copies already obtained by others; server-side permanent erasure is not claimed. The GitHub account name remains in repository ownership and commit attribution, with a GitHub `noreply` email address.
