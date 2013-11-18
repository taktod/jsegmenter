package com.ttProject.jsegmenter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

import com.ttProject.chunk.IMediaChunk;
import com.ttProject.chunk.IMediaChunkManager;
import com.ttProject.media.Manager;
import com.ttProject.media.Unit;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.segment.m3u8.M3u8Manager;

/**
 * JSegmenterのエントリー部
 * @author taktod
 */
public class JSegmenter {
	/** 動作ロガー */
	private static Logger logger = Logger.getLogger(JSegmenter.class);
	/** 入力ファイル名 */
	private static String sourceFile = null;
	/** 各chunkデータにアクセスするときに接頭につけるhttpパス */
	private static String httpPrefix = null;
	/** 出力ファイルprefix(デフォルトはfile、絶対パスをいれると出力先は絶対パスになるが、m3u8に書き込まれるデータはファイル名のみ抽出になる) */
	private static File filePrefix = new File("file");
	/** 出力m3u8ファイル名 */
	private static String m3u8File = null;
	/** 出力データ分割の長さ */
	private static int duration = 10;
	/** 出力分割ファイルデータ数指定(ライブのときとかには、最大出力を設定して制限します。) */
	private static Integer limit = null;
	/** strictがtrueの場合は、リストにでなくなった分割メディアファイルは削除します。falseなら残します */
	private static boolean strict = false;
	/** 出力形式 */
	private String format = null; // 出力形式強制
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		JSegmenter instance = null;
		try {
			Options opt = new Options();
			opt.addOption("help",    false, "Print help for this application");
			opt.addOption("input",    true, "input source(default stdin)");
			opt.addOption("duration", true, "Duration in seconds(default 10 seconds)");
			opt.addOption("prefix",   true, "prefix for generate files(default 'file')");
			opt.addOption("m3u8",     true, "name of generate m3u8 file");
			opt.addOption("http",     true, "name of http prefix for generate files");
			opt.addOption("live",    false, "only latest 3 files on m3u8(same as '-limit 3 -strict')");
			opt.addOption("strict",  false, "delete files not on list");
			opt.addOption("limit",    true, "limit the number of files on m3u8");
			opt.addOption("format",  false, "force format for data.(mpegts mp3 aac)");
			BasicParser parser = new BasicParser();
			CommandLine cl = parser.parse(opt, args);
			if(cl.hasOption("help") || args == null || args.length == 0) {
				HelpFormatter f = new HelpFormatter();
				f.printHelp("./segmenter.sh -input <input file> -m3u8 <m3u8 file> -http <path to generate directory>", opt);
				System.exit(0);
			}
			instance = new JSegmenter(cl);
			Thread.sleep(1000);
			instance.execute();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			instance.close();
		}
	}
	/**
	 * コンストラクタ
	 * @param cli
	 */
	public JSegmenter(CommandLine cli) {
		if(cli.hasOption("input")) {
			sourceFile = cli.getOptionValue("input");
		}
		if(cli.hasOption("duration")) {
			duration = Integer.valueOf(cli.getOptionValue("duration"));
		}
		if(cli.hasOption("prefix")) {
			filePrefix = new File(cli.getOptionValue("prefix"));
		}
		if(cli.hasOption("m3u8")) {
			m3u8File = cli.getOptionValue("m3u8");
			if(!cli.hasOption("http")) {
				httpPrefix = "";
			}
			else {
				httpPrefix = cli.getOptionValue("http");
				if(httpPrefix.lastIndexOf(0) == '/') {
					httpPrefix += "/";
				}
			}
		}
		if(cli.hasOption("live")) {
			limit = 3;
			strict = true;
		}
		else {
			if(cli.hasOption("strict")) {
				strict = true;
			}
			if(cli.hasOption("limit")) {
				limit = Integer.valueOf(cli.getOptionValue("limit"));
			}
		}
		if(cli.hasOption("format")) {
			// 出力フォーマット指定
			format = cli.getOptionValue("format");
		}
		// 必要な情報解析完了
		logger.info(toString());
	}
	/**
	 * 実行処理
	 * 基本的にmyLib.packetにByteBufferの形でデータをいれていくと分割されたパケット情報がでてくる
	 * それを順番にファイルにしていけばよい。
	 */
	public void execute() throws Exception {
		// 必要なファイルを読みこんでMediaManagerに処理を依頼する。
		ReadableByteChannel source;
		if(sourceFile == null) {
			// stdInから処理を実行する。
			source = Channels.newChannel(System.in);
		}
		else {
			// ファイルから処理を実行する。
			source = FileReadChannel.openFileReadChannel(sourceFile);
		}
		// とりあえず拡張子をみて判断することにします。
		// urlの場合はコンテンツタイプも判断材料にします。
		// 入力コンテンツに対応した方法で読み込みを実施し、chunkManagerにまわせばよい感じになります。
		ManagerFinder finder = new ManagerFinder(source);
		Manager<?> manager = finder.findAnalyzeManager();
		if(manager == null) {
			throw new Exception("解析Managerが決定しませんでした。");
		}
		IMediaChunkManager chunkManager = finder.findChunkManager(format);
		if(chunkManager == null) {
			throw new Exception("分割Chunk作成Managerが決定しませんでした。");
		}
		logger.info("解析Manager:" + manager.getClass());
		logger.info("Chunk作成Manager:" + chunkManager.getClass());
		/** 追記番号 */
		int increment = 0;
		/** m3u8データ構成オブジェクト */
		M3u8Manager m3u8Manager = null;
		if(m3u8File != null) {
			m3u8Manager = new M3u8Manager(m3u8File, (float)duration, limit);
		}
		// 分割長設定
		chunkManager.setDuration(duration);
		// 出力マネージャーをchunkManagerをなんとかする。
		while(source.isOpen()) {
			// 解析していきます。
			ByteBuffer buffer = ByteBuffer.allocate(65536);
			source.read(buffer);
			buffer.flip();
			if(buffer.remaining() == 0) {
				System.out.println("入力データが0でした。");
				// ファイル系の読み込みなら・・・
				if(source instanceof IFileReadChannel) {
					// 終端まできているので、おわった物とします。
					break;
				}
				// それ以外の場合はまだデータがくる可能性があるので、1秒まってからやり直すことにします。
				Thread.sleep(1000);
				continue;
			}
			List<?> unitsList = null;
			unitsList = manager.getUnits(buffer);
			if(unitsList.size() != 0) {
				for(Object obj : unitsList) {
					if(obj instanceof Unit) {
						Unit unit = (Unit) obj;
						// このunitをchunkManagerに割り当てる
						IMediaChunk chunk = chunkManager.getChunk(unit);
						if(chunk != null) {
							increment = writeChunk(chunkManager, increment,
									m3u8Manager, chunk);
						}
					}
				}
			}
		}
		// 残っているデータがある場合は、処置しないとだめ。
		IMediaChunk chunk = chunkManager.close();
		if(chunk != null) {
			System.out.println("最終chunkがのこってた。");
			writeChunk(chunkManager, increment, m3u8Manager, chunk);
		}
		// 終端を書き込んでおく
		if(m3u8File != null) {
			m3u8Manager.writeEnd();
		}
		System.out.println("完了しました");
	}
	private int writeChunk(IMediaChunkManager chunkManager, int increment,
			M3u8Manager m3u8Manager, IMediaChunk chunk)
			throws FileNotFoundException, IOException {
		// chunkができているので、あとはそれを書き出すだけ。
		increment ++;
		String targetFile = filePrefix.getAbsolutePath() + "_" + increment + "." + chunkManager.getExt();
		String targetHttp = httpPrefix + filePrefix.getName() + "_" + increment + "." + chunkManager.getExt();
		FileOutputStream chunkFile = null;
		try {
			chunkFile = new FileOutputStream(targetFile);
			chunkFile.getChannel().write(chunk.getRawBuffer());
		}
		finally {
			if(chunkFile != null) {
				try {
					chunkFile.close();
				}
				catch(Exception e) {}
				chunkFile = null;
			}
		}
		if(m3u8File != null) {
			m3u8Manager.writeData(
					targetFile,
					targetHttp,
					chunk.getDuration(),
					increment,
					false
			);
		}
		return increment;
	}
	public void close() {
		
	}
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("sourceFile:").append(sourceFile).append("\n");
		str.append("httpPrefix:").append(httpPrefix).append("\n");
		str.append("filePrefix:").append(filePrefix).append("\n");
		str.append("m3u8File:").append(m3u8File).append("\n");
		str.append("duration:").append(duration).append("\n");
		str.append("limit:").append(limit).append("\n");
		str.append("strict:").append(strict).append("\n");
		str.append("format:").append(format).append("\n");
		return str.toString();
	}
}
