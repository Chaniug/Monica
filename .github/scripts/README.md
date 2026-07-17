# README 自动更新脚本

`update_afdian_sponsors.py` 从爱发电开放接口读取支持者和订单数据，并更新项目根目录 `README.md` 中的 `afdian-sponsors` 标记区块。

## GitHub 配置

在仓库的 `Settings → Secrets and variables → Actions` 中创建 Repository secret：

| 名称 | 内容 |
| --- | --- |
| `AFDIAN_TOKEN` | 爱发电开放接口 token |

爱发电 user ID 已作为公开标识写入工作流。token 只通过 Secret 注入，禁止提交到仓库。

工作流每天北京时间 10:17 自动运行，也可在 Actions 页面手动执行 `Update Afdian sponsors`。

本地验证命令：

```powershell
$env:AFDIAN_TOKEN = "从本地安全位置读取"
python .github/scripts/update_afdian_sponsors.py
Remove-Item Env:AFDIAN_TOKEN
```
