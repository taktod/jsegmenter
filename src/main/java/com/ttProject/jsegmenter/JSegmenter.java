package com.ttProject.jsegmenter;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import com.ttProject.jsegmenter.packet.PacketManager;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.packet.IMediaPacket;
import com.ttProject.segment.m3u8.M3u8Manager;

public class JSegmenter {
	private static String sourceFile = null;
	private static String httpPrefix = null;
	private static File filePrefix = new File("file");
	private static String m3u8File = null;
	private static int duration = 10;
	private static Integer limit = null;
	private static boolean strict = false;
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
			BasicParser parser = new BasicParser();
			CommandLine cl = parser.parse(opt, args);
			if(cl.hasOption("help") || args == null || args.length == 0) {
				HelpFormatter f = new HelpFormatter();
				f.printHelp("./segmenter.sh -input <input file> -m3u8 <m3u8 file> -http <path to generate directory>", opt);
				System.exit(0);
			}
			instance = new JSegmenter(cl);
			instance.execute();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			instance.close();
		}
	}
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
		// 必要な情報解析完了
		System.out.println(toString());
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
		int increment = 0;
		PacketManager manager = new PacketManager(duration);
		M3u8Manager m3u8Manager = null;
		if(m3u8File != null) {
			m3u8Manager = new M3u8Manager(m3u8File, (float)duration, limit);
		}
		// stdinの場合はavailableが0の場合があるみたいです。
		while(source.isOpen()) {
			// 解析していきます。
			ByteBuffer buffer = ByteBuffer.allocate(65536);
			source.read(buffer);
			buffer.flip();
			if(buffer.remaining() == 0) {
				if(source instanceof IFileReadChannel) {
					// fileChannelで残りが0バイトだったら、処理がおわってる
					break;
				}
				Thread.sleep(1000);
				continue;
			}
			System.out.println("bufferの中身:" + buffer.remaining());
			List<IMediaPacket> packets = manager.getPackets(buffer);
			for(IMediaPacket packet : packets) {
				System.out.println("packetを書き込みます");
				increment ++;
				String targetFile = filePrefix.getAbsolutePath() + "_" + increment + manager.getExt();
				String targetHttp = httpPrefix + filePrefix.getName() + "_" + increment + manager.getExt();
				packet.writeData(targetFile, false);
				// m3u8の書き込みが必要なら、処理する。
				if(m3u8File != null) {
					m3u8Manager.writeData(
							targetFile,
							targetHttp,
							packet.getDuration(), increment, false);
				}
			}
		}
		// 処理がおわったら、最終パケットのデータをうけとって、おわる。
		IMediaPacket packet = manager.getCurrentPacket();
		if(packet != null) {
			increment ++;
			String targetFile = filePrefix.getAbsolutePath() + "_" + increment + manager.getExt();
			String targetHttp = httpPrefix + filePrefix.getName() + "_" + increment + manager.getExt();
			packet.writeData(targetFile, false);
			// m3u8の書き込みが必要なら、処理する。
			if(m3u8File != null) {
				m3u8Manager.writeData(
						targetFile,
						targetHttp,
						packet.getDuration(), increment, true);
			}
		}
		System.out.println("完了しました");
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
		return str.toString();
	}
}
