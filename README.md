# PatPatGeyserExtension 0.2.0

**Geyserの`extensions/`へ入れる版です。Paper用PatPatGeyserAddonは不要です。**

統合版のしゃがみ＋空の手でのインタラクトをPatPat Modと同じV2通信へ変換し、手のアニメーションは統合版クライアント内にだけ表示します。バックエンドで補助アーマースタンドを生成しません。

## 対応環境

- **Geyser 2.12.1-b1280 / commit `64bb3993b4beef6743af0ab76ee5dfc8e67a0d21`専用**。
- Java 21以上。ユーザーのVelocity＋Geyser環境を対象に、配布済みVelocity版b1280の実際のクラスへコンパイルしています。
- バックエンドは**PatPat Plugin 1.2.5以上**が必要です。PatPat本体は同梱していません。
- Java版で従来の演出を見る人にはPatPatクライアントModが必要です。
- 拡張単体ではFloodgate-Spigotの追加は不要です。既存のGeyser／Floodgate認証設定はそのまま利用します。
- Geyserの公開APIだけでは操作・PatPat独自通信を扱いきれないため、セッション／パケットAPIも使います。**異なるGeyserビルドでは操作変換を停止し、起動ログへ理由を出します。** Geyserの自動更新でビルドが変わった場合は、そのビルドに合わせた拡張の再ビルドが必要です。
- 試験版です。コンパイル／自動テスト済みですが、実際の統合版クライアントと接続した確認はまだ行っていません。詳細はVALIDATION.md。

## 導入

1. 前回のPaper用`PatPatGeyserAddon-0.1.0.jar`を導入している場合は、バックエンドの`plugins/`から外します。**PatPat本体は残してください。** 両方を同時に動かすと演出や操作が重複する可能性があります。
2. `PatPatGeyserExtension-0.2.0.jar`を、Velocity側の **`plugins/Geyser-Velocity/extensions/`** に置きます。Velocity直下の`plugins/`ではありません。
3. 前回の`PatPatBedrock-0.1.0.mcpack`をGeyserの`packs/`へ入れていた場合は、そこから外して構いません。今回はJARからパックを展開して自動登録します。手動パックが同じUUIDで既に登録されている場合、二重登録は避けますが、古いパックが優先されます。
4. Proxyを再起動します。初回に`extensions/patpatbedrock/`へ設定と`PatPatBedrock.mcpack`が生成されます。
5. Geyserの`force-resource-packs: true`を推奨。統合版で入り直し、パックを受け取ってください。
6. **PatPat本体の入ったSurvivalへ移動**し、次のコマンドで確認します。

```text
/patpatbedrock status
/patpatbedrock test text
/patpatbedrock test
```

`backendHello=true`なら、現在接続中のバックエンドのPatPat本体から応答を受信できています。`test text`はパック不要の`PAT`文字、`test`は手アイコンです。見ているエンティティがGeyserに通知されていればその頭上へ、なければ自分の頭上へ表示します。自分の頭上は三人称視点で確認できます。

本体のないロビーでは`backendHello=false`でも正常です。テスト表示自体は利用できますが、実際の撫でる操作にはバックエンドのPatPatが必要です。バックエンド切替・リスポーン／ディメンション変更時には通信状態を再確認します。

## 操作

- 空のメインハンドでしゃがみ、モブを「使う／インタラクト」します。
- 攻撃タップは変換しません。端末の操作モードによっては長押し等が必要です。
- **`/patpatbedrock pat`** でも、照準先としてGeyserに認識されている相手を撫でられます。コマンドではしゃがみ不要ですが、空の手・距離・権限は必要です。照準先が通知されない端末では、インタラクト操作を使用してください。
- 既定の最大距離は3ブロック、クールダウンは600msです。メインハンド／インタラクト重複は共通のクールダウンで抑止します。

## Java版との連携

| 操作した人 | Java版＋PatPat Mod | 統合版 |
|---|---|---|
| 統合版 | PatPat本体の通常パケットによる演出 | 操作者のローカル演出＋周囲は本体からの受信で表示 |
| Java版＋PatPat Mod | 従来どおり | 本体からのV2パケットを受信して表示 |
| Java版・Modなし | 撫でる操作は追加しません | ― |

この版は公開APIを呼ぶ方式ではなく、PatPat Modのクライアント通信を実装する方式です。**PatPat本体の通常のパケット受信処理（リスト・権限・レート制限）を通ります。** `api: true`は拡張自体には必須ではありません。ただし他の制限アドオンが公開APIイベントを利用している場合は、本体側で有効にしておいてください。

### 操作者の演出について

PatPat本体は、通常の撫でるパケットを操作者へ返しません。Java版Modと同様、操作者の手の演出はサーバーの受理を待たずに表示します。そのため、**PatPat側で拒否されても操作者自身には手が見える場合があります**。周囲のプレイヤーには、実際にサーバーから届いたパケットでのみ表示します。設定の`local-preview=false`で操作者の先行表示を無効にできます。

通常のBukkit右クリックイベントへは変換されません。保護が必要な場合は、PatPat本体の権限／リスト設定、またはPatPatのイベントへ対応する制限アドオンで管理してください。通常のモブ右クリック保護と完全に同じ動作になるとは限りません。

## 設定

`plugins/Geyser-Velocity/extensions/patpatbedrock/config.properties`

| 設定 | 既定値 | 内容 |
|---|---|---|
| `enabled` | true | 操作変換と通常の受信演出 |
| `auto-pack` | true | 同梱パックを自動配信。変更はProxy再起動が必要 |
| `require-sneaking` | true | インタラクト操作にしゃがみ必須 |
| `require-empty-hand` | true | 空のメインハンド必須 |
| `allow-players` | true | プレイヤーも撫でられる |
| `max-distance` | 3.0 | バウンディングボックスまでの最大距離 |
| `cooldown-ms` | 600 | 連打抑止 |
| `duration-ms` | 800 | 手の表示時間 |
| `frame-ms` | 100 | フレーム間隔 |
| `height-offset` | 0.15 | 対象の頭上からの高さ。実機で調整可能 |
| `max-active-per-viewer` | 32 | プレイヤー1人あたりの同時演出数 |
| `local-preview` | true | 操作者の先行演出 |
| `frames` | U+E2A0～U+E2A3 | カンマ区切りの手の4フレーム |

通常の設定は`/patpatbedrock reload`で反映できます。再読込時は演出を削除し、バックエンドの通信状態も再確認します。

| 権限 | 設定する場所 | 内容 |
|---|---|---|
| `patpatbedrock.use` | Proxy/Geyser側 | 操作とテストコマンド。既定true |
| `patpatbedrock.admin` | Proxy/Geyser側 | reload。既定では明示的な付与が必要。コンソールからも実行可能 |
| `patpat.pat`等 | バックエンド側 | PatPat本体の設定に従う権限 |

## リソースパックの競合

前回と同じ手パックを同梱しています。`font/glyph_E2.png`全体を置き換えるため、別の絵文字パックが同じページを使う場合は統合が必要です。U+E2A0～U+E2A3は、256×256画像の行10・列0～3（16×16セル）です。

既存パックへ4セルを合成して配信する場合は、`auto-pack=false`にし、統合済みパックを既存の配信経路で配信してください。`frames`も必要に応じて変更できます。

生成された`PatPatBedrock.mcpack`は起動時に上書きしません。独自編集を保持します。パック更新時はmanifestのversionを上げ、統合版を再接続してください。

## 問題の切り分け

- 起動ログに`PatPat extension ready for Geyser b1280`が出る → 対応ビルドとしてフックを有効にできています。
- `Bridge disabled` → 対応外のGeyserビルドです。ビルド番号とcommitを確認。
- `backendHello=false` → 接続先にPatPat本体がない、古い、またはProxyでPatPatの独自チャンネルが転送されていません。
- `test text`も見えない → 位置、Geyser／統合版のゼロスケール名前表示を確認。
- `test text`だけ見える → パック受信・glyph_E2競合・キャッシュを確認。
- 自分には手が見えるのに他人へ伝わらない → 本体の権限、リスト、レート制限、Modバージョンを確認。自分の先行演出は受理確認ではありません。
- `geyserdisplayentity`は不要です。ログにあった`JAVA_ENTITY_TYPES`のエラーが出る拡張とは別物で、今回の拡張はそのフィールドを参照しません。

## ビルド

JDK 21以上、Maven 3.9以上、Python 3.10以上で実行します。

```sh
python scripts/prepare_build.py
mvn -B verify
```

最初のスクリプトはGeyserのb1280配布JARを取得し、公開SHA-256と一致することを確認してローカルのコンパイル用リポジトリへ配置します。Geyser本体や依存ライブラリーを拡張JARへ埋め込みません。成果物は`target/PatPatGeyserExtension-0.2.0.jar`です。push／PR／手動実行用のGitHub Actionsも同梱しています。

手パックの元画像とmanifestは`resource-pack/`、元のベクターアイコンは`art/hand.svg`です。再生成する場合はPillowをインストールし、`python scripts/build_pack.py`を実行して、生成された`dist/PatPatBedrock-0.1.0.mcpack`を`src/main/resources/PatPatBedrock.mcpack`へコピーしてからビルドしてください。拡張の版番号と同梱パックの版番号は独立しています。

## ライセンス・参照

拡張と独自の手アイコン: MIT。PatPat本体のコードやJARは同梱せず、公開されているV2通信形式と接続します。

- [Geyser Extensions](https://geysermc.org/wiki/geyser/extensions/)
- [Geyser b1280のcommit](https://github.com/GeyserMC/Geyser/commit/64bb3993b4beef6743af0ab76ee5dfc8e67a0d21)
- [PatPat Plugin](https://github.com/LopyMine/PatPat-Plugin)
- PatPatプロトコル確認元: `8a5810960441fee6906447b074406247eda0c57c`のHelloPacketHandler、PatPacketV2、PatPacketHandler。
