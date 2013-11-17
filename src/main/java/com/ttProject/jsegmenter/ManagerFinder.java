package com.ttProject.jsegmenter;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import com.ttProject.chunk.IMediaChunkManager;
import com.ttProject.chunk.aac.AacChunkManager;
import com.ttProject.chunk.aac.analyzer.AacFrameAnalyzer;
import com.ttProject.chunk.mp3.Mp3ChunkManager;
import com.ttProject.chunk.mp3.analyzer.Mp3FrameAnalyzer;
import com.ttProject.chunk.mpegts.MpegtsChunkManager;
import com.ttProject.chunk.mpegts.analyzer.MpegtsPesAnalyzer;
import com.ttProject.media.Manager;
import com.ttProject.media.aac.AacManager;
import com.ttProject.media.mp3.Mp3Manager;
import com.ttProject.media.mpegts.MpegtsManager;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * 入力ソースからどういうデータなのか判定するクラス
 * @author taktod
 */
public class ManagerFinder {
	/** 基本読み込みデータ */
	private final ReadableByteChannel targetChannel;
	/** 見つけたmanager */
	private Manager<?> findedManager = null;
	private IMediaChunkManager chunkManager = null;
	/**
	 * コンストラクタ
	 * @param channel
	 */
	public ManagerFinder(ReadableByteChannel channel) {
		targetChannel = channel;
	}
	/**
	 * 動作マネージャーを応答します
	 * @return
	 */
	public Manager<?> findAnalyzeManager() throws Exception {
		// すでに確認済みの場合は前のデータを応答します。
		if(findedManager != null) {
			return findedManager;
		}
		// 読み込みデータがなにであるか確認する。
		// 中身をみて、どの拡張子であるか判定したい。
		// 8バイトみておく。(1秒timeout)
		ByteBuffer buffer = BufferUtil.safeRead(targetChannel, 8, 1000);

		byte[] data = new byte[8];
		buffer.get(data);
		buffer.position(0); // 始めに戻しておく。
		// 以下 生データ解析
		// 中身が0x47ではじまっていたらmpegts
		if(data[0] == 0x47) {
			System.out.println("mpegtsか？");
			findedManager = new MpegtsManager();
		}
		// FLVではじまっていたらflv
		else if(data[0] == 'F'
				&& data[1] == 'L'
				&& data[2] == 'V'){
			System.out.println("flvか？");
			throw new Exception("flvの追記解析はライブラリをまだ読み込んでいないので、追記しません。");
		}
		// ID3ではじまっていたらmp3
		else if(data[0] == 'I'
				&& data[1] == 'D'
				&& data[2] == '3'){
			System.out.println("mp3か？");
			findedManager = new Mp3Manager();
		}
		// さらに4バイト読み込む
		// ftypが5バイト目以降にある場合
		else if(data[4] == 'f'
				&& data[5] == 't'
				&& data[6] == 'y'
				&& data[7] == 'p'){
			System.out.println("mp4か？");
			throw new Exception("mp4の追記解析は未サポートです。");
		}
		else {
			// ここまできて決まっていない場合は、拡張子やfileContentsで決めないとだめ。
			if(!(targetChannel instanceof IFileReadChannel)) {
				throw new Exception("標準入力解析ですが、ファイルフォーマットが決定しませんでした。");
			}
			// 参考程度にしかならなさそうなので、拡張子をみることにします。
/*			if(targetChannel instanceof URLFileReadChannel) {
				URLFileReadChannel urlReadChannel = (URLFileReadChannel) targetChannel;
				String contentType = urlReadChannel.getContentType();
				if("video/x-flv".equalsIgnoreCase(contentType)) {
					// flvであると思われます。
				}
				else if("video/mpeg".equalsIgnoreCase(contentType)) {
					// mpegtsであると思われます。
				}
			}*/
			IFileReadChannel fileReadChannel = (IFileReadChannel)targetChannel;
			String uriString = fileReadChannel.getUri();
			if(uriString.endsWith(".ts")) {
				System.out.println("mpegts");
				findedManager = new MpegtsManager();
			}
			else if(uriString.endsWith(".flv")) {
				System.out.println("flv");
				throw new Exception("flvの追記解析はライブラリをまだ読み込んでいないので、追記しません。");
			}
			else if(uriString.endsWith(".aac")) {
				System.out.println("aac");
				findedManager = new AacManager();
			}
			else if(uriString.endsWith(".mp3")) {
				System.out.println("mp3");
				findedManager = new Mp3Manager();
			}
			else if(uriString.endsWith(".mp4")) {
				System.out.println("mp4");
				throw new Exception("mp4の追記解析は未サポートです。");
			}
			else if(uriString.endsWith(".webm")) {
				System.out.println("webm");
				throw new Exception("webmの追記解析はライブラリをまだ読み込んでいないので、追記しません。");
			}
			else if(uriString.endsWith(".mkv")) {
				System.out.println("mkv");
				throw new Exception("mkvの追記解析はライブラリをまだ読み込んでいないので、追記しません。");
			}
		}
		// 初期データを追記しておきます。(たぶん解析まではいたらないと思うので、このまま投げておく。)
		List<?> units = findedManager.getUnits(buffer);
		if(units != null && units.size() != 0) {
			throw new Exception("初期セットアップでunit応答してしまいました。想定外です。");
		}
		return findedManager;
	}
	/**
	 * chunk生成マネージャーを応答します
	 * @param format 候補format(強制したいときはいれます)
	 * @return
	 * @throws Exception
	 */
	public IMediaChunkManager findChunkManager(String format) throws Exception {
		if(findedManager == null) {
			throw new Exception("入力ソースが決定していません。findAnalyzeManagerを先に実行してください。");
		}
		// すでに以前解析したchunkManagerがあるので、それを利用する
		if(chunkManager != null) {
			return chunkManager;
		}
		// findedManagerとformatが一致するか確認しなければいけない。
		if(format == null) {
			// format指定がない場合
			if(findedManager instanceof Mp3Manager) {
				// mp3ManagerなのでchunkManagerもmp3にする。
				Mp3ChunkManager mp3ChunkManager = new Mp3ChunkManager();
				mp3ChunkManager.addMp3FrameAnalyzer(new Mp3FrameAnalyzer());
				chunkManager = mp3ChunkManager;
			}
			else if(findedManager instanceof AacManager) {
				// aacManagerなのでchunkManagerもaacにする。
				AacChunkManager aacChunkManager = new AacChunkManager();
				aacChunkManager.addAacFrameAnalyzer(new AacFrameAnalyzer());
				chunkManager = aacChunkManager;
			}
			else if(findedManager instanceof MpegtsManager) {
				// mpegtsなのでchunkManagerもmpegtsにする。
				MpegtsChunkManager mpegtsChunkManager = new MpegtsChunkManager();
				mpegtsChunkManager.addPesAnalyzer(new MpegtsPesAnalyzer());
				chunkManager = mpegtsChunkManager;
			}
		}
		else {
			// こちらの場合は入力ソースに応じた解析プログラムをいれないとだめ。
/*			if("mpegts".equals(format)) {
				chunkManager = new MpegtsChunkManager();
			}
			else if("aac".equals(format)) {
				chunkManager = new AacChunkManager();
			}
			else if("mp3".equals(format)) {
				chunkManager = new Mp3ChunkManager();
			}*/
		}
		return chunkManager;
	}
}
