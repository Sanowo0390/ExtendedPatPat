追加OGGは`extensions/patpatbedrock/sounds/`へ置けます（半角小文字・数字・_・-のファイル名）。そのフォルダーの音がランダム候補になります。固定再生は例:`sound-event=patpatbedrock.sound.pat1`。変更後はProxy再起動と再接続が必要です。

# PatPatGeyserExtension 0.4.0 の追加機能

添付のpatpat.gifと3つのOGGを既定素材として同梱。新規設定は位置X/Y/Z=0、サイズ1.0です。既存の設定とimages/hand.gifは上書きしません。素材を更新する場合はimages/hand.gifを添付GIFへ置き換えてください。

`/patpatbedrock reload`は設定（位置、サイズ、音量、ピッチ、有効無効）を再読込します。ゲーム内ではProxy側patpatbedrock.admin権限が必要、Proxyコンソールでは`patpatbedrock reload`です。画像・音素材のパック更新にはProxy再起動と統合版の再接続が必要です。

```properties
image-scale=1.0
offset-space=view
offset-x=0.0
offset-y=0.0
offset-z=0.0
sound-enabled=true
sound-event=patpatbedrock.pat
sound-volume=1.0
sound-pitch=1.0
```

patpatbedrock.patは同梱3音からBedrockがランダム選択します。sound-eventには他のBedrockサウンド識別子も指定可能です。表示テストでも音を再生し、test textでは鳴らしません。

---

# PatPatGeyserExtension 0.3.1

Geyserの`extensions/`へ入れるPatPat拡張です。統合版で撫でる操作と手の表示を追加し、Java版PatPat Modとも通信します。Paper用PatPatGeyserAddonは不要です。

**0.3.0から、PNG・GIFを貼った専用エンティティで表示します。サイズとX/Y/Z位置を調整でき、画像から統合版パックを自動生成します。既定の画像方式は絵文字フォントを上書きしません。**

## 0.3.1の修正

画像のカメラ回転の符号を修正し、エンティティのbody yawを相殺する公式のビルボード式へ変更しました。見る方向によって板が斜めになり薄く見える問題を修正する変更です。

位置は既定で見る人を基準にします。**Xプラスで対象の中心から自分側へ、Yプラスで上へ、Zプラスで自分から見て右へ**移動します。複数の統合版プレイヤーが見る場合、それぞれの視点に合わせて計算します。生成パックも更新するため、JAR置換後はProxy再起動と統合版の再接続が必要です。

## 必要な環境

- **Geyser 2.12.1-b1280 / commit `64bb3993b4beef6743af0ab76ee5dfc8e67a0d21`専用**。内部セッション／パケットAPIを使うため、別ビルドでは操作変換を停止します。
- Java 21以上。Velocity版b1280の実配布JARに対してビルドしています。
- 撫でる操作を使うバックエンドに**PatPat Plugin 1.2.5以上**。
- Java版で従来の演出を見るプレイヤーはPatPatクライアントModが必要です。
- 既存のGeyser／Floodgate認証設定を利用します。拡張単体のためにFloodgate-Spigotを追加する必要はありません。
- 試験版です。自動テストはVALIDATION.mdを参照。実クライアントでの新しい画像モデルの見え方は未検証です。

## 導入・更新

1. 古いPatPatGeyserExtensionのJARを外し、新しい`PatPatGeyserExtension-0.3.1.jar`を**`plugins/Geyser-Velocity/extensions/`**へ配置。
2. Paper用PatPatGeyserAddonが残っていれば外します。**バックエンドのPatPat本体は残してください。**
3. Proxyを再起動。`extensions/patpatbedrock/`に初期GIF、設定、新しいパックを自動生成します。旧設定には新しいキーを追加し、既存の値は保持します。
4. 統合版で入り直して生成パックを受信。Geyserの`force-resource-packs: true`を推奨します。
5. PatPat本体のあるSurvivalで次を実行します。

```text
/patpatbedrock status
/patpatbedrock test
```

`status`の`renderer=image`を確認してください。`backendHello=true`はPatPat本体から応答を受信できた状態です。`false`でも表示テストは可能ですが、実際の撫でる操作はできません。本体のないロビーではfalseで正常です。

`/patpatbedrock test`は照準先の頭上へ、自分にだけ表示します。照準先がGeyserに通知されていなければ自分の頭上です。三人称視点で確認できます。`test text`は引き続きパック不要の文字表示テストです。

旧版の`PatPatBedrock-0.1.0.mcpack`をGeyserの`packs/`へ手動配置していた場合、画像方式では不要なので外してください。旧glyphパックを残すとそのフォント上書きも残ります。

## 位置・サイズを調整

編集するファイル:

```text
plugins/Geyser-Velocity/extensions/patpatbedrock/config.properties
```

例（1.2ブロック幅へ拡大し、対象の頭上から0.2ブロック下へ）:

```properties
renderer=image
offset-space=view
image-scale=1.2
offset-x=0.25
offset-y=-0.2
offset-z=0.0
```

- `image-scale`: 透明な正方形キャンバスの幅／高さをブロック単位で指定。既定0.8、範囲0.05〜8。画像に余白が多いほど、見える絵の部分は小さくなります。
- `offset-y`: 対象の当たり判定の上端を基準とした画像中心の高さ。正で上、負で下。文字ネームタグの高さ補正は使いません。
- `offset-space=view`（既定）: `offset-x`プラスで対象から見る人側へ水平移動、マイナスで奥へ移動。`offset-z`プラスで見る人から見て右、マイナスで左へ移動します。X／Zで高さは変わりません。
- `offset-space=world`: 旧0.3.0と同じワールドX／Z軸方向のずれを使う場合に指定します。
- 自分の頭上や真上から見る場合は、水平位置が重なるため見る人のyawを使います。画像の回転自体は統合版の実際のカメラ角度に追従します。
- 非正方形の画像は縦横比を保ったまま中央へ収め、残りを透明にします。描画モデルはカメラを向く板です。
- 旧`height-offset`は`offset-y`の初期値へ引き継ぎます。以後は`offset-y`を編集してください。

変更後、Proxyコンソールで実行:

```text
patpatbedrock reload
```

ゲーム内では`/patpatbedrock reload`です。Proxy側の`patpatbedrock.admin`権限が必要です。位置・サイズ・表示時間は再接続せずに反映できます。

## PNG・GIFへ差し替える

1. 使用する画像を次のフォルダーへ置きます。

```text
plugins/Geyser-Velocity/extensions/patpatbedrock/images/
```

2. ファイル名を設定します。

```properties
image-file=images/custom.gif
```

PNGなら`image-file=images/custom.png`です。

3. **Proxyを再起動し、統合版で再接続**してください。ファイル内容からパックを再生成し、変更に応じてキャッシュ用のパックversionも変更します。画像やファイル名、rendererの変更をreloadだけで配信することはできません。

- PNG: 透過に対応した静止画。
- GIF: 最大128フレーム。元のフレーム待ち時間、部分更新、残像を防ぐdisposal合成に対応。
- GIFの更新精度は最大20fps。50ms未満の待ち時間は50msへ、0msは100msへ調整します。
- 元のキャンバスは最大1024×1024、ファイルサイズは16MiBまで。各コマは縦横比を維持して128×128の透過PNGに変換します。
- 不正／未対応画像の場合は起動ログへ理由を出し、通常の変換を停止します。
- 初期の`images/hand.gif`は上書きしません。独自画像や既存の設定ファイルを保持します。

表示時間と繰り返し:

```properties
# 0ならGIF全体を1周表示。PNGは800ms。
duration-ms=0
# duration-msを長くした場合、アニメーションを繰り返すか
image-loop=true
```

例: `duration-ms=2000`と`image-loop=true`で2秒間繰り返します。`image-loop=false`なら最後のコマで停止し、表示時間の終了時に消えます。GIF内の繰返し回数指定は使用せず、この設定で管理します。旧設定の`duration-ms=800`は保持されるため、長いGIF全体を表示したい場合は0へ変更してください。

生成パックは`extensions/patpatbedrock/generated/`に保存します。`auto-pack=false`なら自動配信を止め、生成されたパックを別の配信経路で利用できます。再起動時の削除・再生成は不要です。

## 撫でる操作

- 空のメインハンドでしゃがみ、モブを「使う／インタラクト」します。攻撃タップは変換しません。
- `/patpatbedrock pat`も利用できます。Geyserが照準先として認識している対象が必要です。コマンドはしゃがみ不要です。
- 最大距離は既定3ブロック。クールダウン600msで重複するインタラクトを抑止します。
- `backendHello=false`なら、接続先のPatPat本体とまだ通信できていません。表示テストの成功と本体との通信成功は別々に確認してください。

## 設定一覧

| キー | 既定値 | 内容 |
|---|---|---|
| enabled | true | 操作変換と通常の受信演出 |
| renderer | image | `image`はPNG/GIFモデル、`glyph`は旧文字方式 |
| image-file | images/hand.gif | 設定フォルダー内の画像への相対パス |
| image-scale | 0.8 | 正方形キャンバスの幅・高さ（ブロック） |
| offset-space | view | `view`は見る人基準、`world`はワールド軸 |
| offset-x / offset-y / offset-z | 0 / 0.05 / 0 | viewでは、自分側／上／右へ移動 |
| duration-ms | 0 | 0で画像の1周分、それ以外は固定表示時間 |
| image-loop | true | 固定表示時間中にGIFを繰り返す |
| auto-pack | true | 表示用パックを自動登録 |
| required-permission | 空欄 | 追加のProxy権限。既定は不要 |
| require-sneaking | true | インタラクト時にしゃがみ必須 |
| require-empty-hand | true | 空のメインハンド必須 |
| allow-players | true | プレイヤーも撫でられる |
| max-distance | 3.0 | 対象の当たり判定までの最大距離 |
| cooldown-ms | 600 | 連打抑止 |
| max-active-per-viewer | 32 | 統合版プレイヤー1人あたりの最大同時演出数 |
| local-preview | true | 操作者の演出を先行表示 |
| frames / frame-ms | 旧4文字 / 100 | glyph方式でのみ使用 |

status／test／patは既定で追加のProxy権限不要です。制限する場合は`required-permission=patpatbedrock.use`等を設定し、**Velocity側**の権限プラグインで付与してください。バックエンドの権限では代用できません。reloadは`patpatbedrock.admin`、バックエンドのPatPat本体の制限は本体の設定に従います。

## 通信・制限について

この拡張はPatPat Modと同じV2通信を使い、本体の通常のリスト・権限・レート制限を通ります。Java版Modからの受信演出も統合版へ表示します。PatPat公開APIの直接呼出し方式ではなく、`api: true`は拡張単体には必須ではありません。

PatPat本体は通常のパケットを操作者へ返さないため、**操作者だけは本体の受理を待たずに先行表示**します。拒否されても自分には手が見える場合があります。周囲には本体から届いたパケットでのみ表示します。`local-preview=false`で先行表示を無効にできます。

通常のBukkit右クリックイベントを作る方式ではありません。保護が必要な場合はPatPat本体の設定か、PatPatイベントへ対応する制限アドオンで管理してください。

新しい画像モデル方式はGeyserのcustom entity登録APIを使います。`GeyserDisplayEntity`は不要です。旧版の`Registries.JAVA_ENTITY_TYPES`は参照しません。`renderer=glyph`へ戻す場合は旧パックのglyph_E2ページ競合に注意してください。

## ビルド

JDK 21以上、Maven 3.9以上、Python 3.10以上:

```sh
python scripts/prepare_build.py
mvn -B verify
```

準備スクリプトは公式Geyser b1280配布JARを取得し、SHA-256を確認します。Geyser本体や外部ライブラリーは拡張JARへ埋め込みません。画像変換はJava標準ImageIOで行い、サーバーにPythonやPillowは不要です。GitHub Actionsでpush／PR／手動ビルドを実行できます。

初期画像は`src/main/resources/default-hand.gif`、旧パックの画像生成元は`art/`、`resource-pack/`と`scripts/build_pack.py`です。

## 参照・ライセンス

拡張と独自の手アイコンはMIT。PatPat本体のコードやJARは同梱しません。

- [Geyser Extensions](https://geysermc.org/wiki/geyser/extensions/)
- [Geyser Entity API](https://geysermc.org/wiki/geyser/custom-entities/)
- [対象のGeyser commit](https://github.com/GeyserMC/Geyser/commit/64bb3993b4beef6743af0ab76ee5dfc8e67a0d21)
- [PatPat Plugin](https://github.com/LopyMine/PatPat-Plugin) — 通信確認元 `8a5810960441fee6906447b074406247eda0c57c`

ビルボード式の参照: [Microsoft公式カスタム投射物の2Dスプライト例](https://learn.microsoft.com/en-us/minecraft/creator/documents/custom_projectiles?view=minecraft-bedrock-stable)。表示用アニメーション式のみ参照しており、投射物や実験的なBehavior Packは使用しません。
