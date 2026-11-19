# Release publishing / リリース公開

## 日本語

公開時にブラウザへログインする代わりに、GitHub連携から明示的な公開指示をコミットし、GitHub Actionsがビルド・署名・Release公開を行います。GitHub認証にはActionsが発行するGITHUB_TOKENを使い、個人用アクセストークンは不要です。

初回だけ、リポジトリの Settings → Secrets and variables → Actions に以下を登録します。

| Secret | 内容 |
| --- | --- |
| APK_RELAY_KEYSTORE_BASE64 | **公開済みAPKと同じ署名鍵**を含むkeystoreのBase64 |
| APK_RELAY_STORE_PASSWORD | keystoreのパスワード |
| APK_RELAY_KEY_PASSWORD | 秘密鍵のパスワード |
| APK_RELAY_KEY_ALIAS | 既存の鍵のalias |

鍵やパスワードをソース、公開指示ファイル、チャットへ貼り付けないでください。新しい鍵やデバッグ鍵を生成して代用しないでください。

次回以降の公開手順:

1. app/build.gradle のversionNameとversionCodeを更新します。
2. 公開したいソースがmainに反映された後、GitHub連携から .github/release-request.json を作成または更新してmainへコミットします。

例（1.3を公開する場合。現在の1.2を再公開する指示ではありません）:

```json
{
  "version": "1.3",
  "notes": "変更内容をここに記載します。"
}
```

このファイルの変更だけが公開を開始します。通常のソース変更や、このワークフローの導入では公開しません。

Actionsは公開済みv1.2のAPKと署名証明書を照合し、アプリID・バージョン名・v1.2より大きいversionCodeも検証します。以降の公開でもversionCodeは直前の公開版より増やしてください。鍵が未登録・不一致の場合は公開前に失敗します。APK・ソースZIP・SHA256SUMS.txtを下書きReleaseへ添付してから公開します。既存Releaseは上書きしません。

この変更は初期設定用です。Secrets登録、mainへの導入、実際の公開は未実施です。失敗した実行はActionsから再実行できます。添付後の公開に失敗して下書きが残った場合は、その下書きを確認してから復旧します。

## English

Commit an explicit release request through the connected GitHub integration. GitHub Actions builds, signs, and publishes the release using its built-in GITHUB_TOKEN; no personal access token or browser login is required for each release.

One-time setup: register the four repository Actions secrets listed above. Use the **existing release keystore**, its store password, key password, and key alias. Do not generate a replacement signing key or commit credentials.

For each release, update versionName and increase versionCode beyond the previous release, commit the intended source to main, then create or update .github/release-request.json on main with the version and release notes shown in the example. Only changes to this request file trigger publishing. Installing the workflow alone does not publish anything.

The workflow checks the certificate against the published v1.2 APK, checks the application ID and version metadata, and requires a versionCode above v1.2. Missing or mismatched signing credentials stop publication. It uploads the APK, source ZIP, and checksums to a draft before publishing and refuses to overwrite an existing release.

Secrets setup, installation on main, and an actual release have not been performed as part of this initial change. Failed jobs can be rerun from Actions. If publication fails after a draft was created, inspect that draft before recovery.
