名前：jsegmenter

mavenのプロジェクトをつくったので、それを利用して作り直してみました。

ライセンス：MITライセンスとします。

作者：taktod
blog: http://poepoemix@blogspot.com
twitter: http://twitter.com/taktod

使い方

java 1.6以降とmaven3が必要です。
maven2を使う場合はpom.xmlの内容を修正してください。
packageコマンドを実行するとtargetの中にtarget/jsegmenter/jsegmenter.shができあがるので、実行してください。
$ mvn package
あとは元のjsegmenterとほぼ同じです。

肝は次のとおりです。

プログラムの考え方の基本：
myLib.packetのPacketシリーズは次のようになっています。
IMediaPacketManagerのgetPacketsにメディアデータのByteBufferデータを次々におくると
分割されたIMediaPacketのデータが応答されます。

分割されてきた応答データをファイルに書き込みつつ、m3u8ファイルも構築していくことで
iOS用のHttpLiveStreamingを作成していきます。

11/17プログラムを更新しました。
１：古いjsegmenterの場合は、単純にmpegtsのpcrのtimestampのみをみて分割していました。
早いけど、分割したデータだけで成立しないことがありました。
今回のプログラムでは、myLib.packetではなくmyLib.chunkという新しくつくったプログラムをベースにして動作しています。
こちらでは、きちんとメディアデータを確認し、分割したsegmentには必要な映像データと音声データがきちんと入るようにしてあります。

２：新しいjsegmenterではadtsによるaacのファイルも扱えるようにしました。

３：これはまずい点ですが、終端のデータの処理がうまくいっていません。
今後の修正目標となります。