# ExtendedPatPat Paper

Paper/Purpur 26.3（Java 25）向け、**リソースパック不要のマップ画像アニメーション**です。

## 導入

`ExtendedPatPat-Paper-0.6.1.jar`をPaper/Purpurの`plugins/`に配置し再起動。ほかのPatPat版とは同時に導入しないでください。

- 設定：`plugins/ExtendedPatPatPaper/config.yml`
- コマンド：`/extendedpatpat`（`/epp`）
- 権限：`extendedpatpat.use`、`extendedpatpat.admin`

## 表示

手の画像はJARに128×128の地図色データとして内包します。表示相手ごとに透明な額縁を作り、フレームごとの地図を切り替えます。BukkitのMapViewはスキン・フレーム単位で生成し、`map-ids.properties`にIDを記録して再起動時も再利用します。額縁は一時的で保存しません。

URL・追加パック・クライアントModは不要。透明度、色数、向き、サイズはバニラ地図の制約を受けます。ViaVersionがPaper側に導入されていれば接続元バージョン判定を利用します。Proxy側のみの場合の判定と実機描画は未検証です。

サウンドはバニラMinecraftの音を利用します。Java版画像追加・スキン選択：[resourcepack/README.md](../resourcepack/README.md)。

原作由来素材のライセンス：[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

## 個人のON/OFF

`/extendedpatpat toggle [on|off]` は送信・受信・Pat対象の許可を同時に切り替えます。OFF時は自分の表示中の手と、自分を対象にした表示をすぐ削除します。設定は `config.yml` に保存されます。
