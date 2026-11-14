# APK Relay

[English](README.md)

## 概要

インストール権限を持たないアプリで開いたAPKファイルを受け取り、本アプリに付与された権限を使ってAndroid標準のパッケージインストーラへ橋渡しすることで、ワンストップでのインストールを可能にします。本アプリにランチャーアイコンやメイン画面はありません。

Android 8.0以降。単体APKにのみ対応。

## 使い方

- APKを開く、または共有して **APK Relay** を選択。
- 初回は「この提供元のアプリを許可」を設定して戻る。
- Android標準のパッケージインストーラでインストール。

## ビルド

JDK 17とAndroid SDK 35でビルドできます。

```sh
./gradlew assembleRelease
```

生成されたAPKは、インストール・配布前に署名が必要です。

## 備考

**ChatGPTアプリでの検証**

| 結果 | リンクの例 |
| --- | --- |
| ✅ APK Relayへ受け渡し可能 | `sandbox:/…/app.apk` |
| ❌ リクエストが拒否される | `https://chatgpt.com/api/library/files/…/download` |
| ❌ ブラウザへ遷移しログインが必要 | `https://chatgpt.com/library?file_id=…&account_id=…` |

## ライセンス

独自コードとアイコンは[Unlicense](LICENSES/Unlicense.txt)です。

AndroidX Coreとその依存ライブラリ、Gradle Wrapperには[Apache 2.0](LICENSES/Apache-2.0.txt)が適用されます。
