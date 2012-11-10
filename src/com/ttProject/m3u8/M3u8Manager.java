package com.ttProject.m3u8;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.Segmenter;

/**
 * m3u8のデータを処理するためのマネージャー
 * @author taktod
 */
public class M3u8Manager {
	private final String header;
	private final String allowCache;
	private final String targetDuration;
	private final List<M3u8Element> elementData;
	public M3u8Manager() {
		header = "#EXTM3U";
		allowCache = "#EXT-X-ALLOW-CACHE:NO";
		targetDuration = "#EXT-X-TARGETDURATION:" + Segmenter.getDuration();
		if(Segmenter.getLimit() != null) {
			elementData = new ArrayList<M3u8Element>();
		}
		else {
			// ファイルに先頭の情報を書き込む
			elementData = null;
			try {
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(Segmenter.getM3u8File(), false)));
				pw.println(header);
				pw.println(allowCache);
				pw.println(targetDuration);
				pw.close();
				pw = null;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	// 指定があれば、一定以上のm3u8データを書き込まないという処理が必要
	// limitがなければ、追記でどんどんappendしていけばOK
	// 終端処理も必要。
	public void writeData(String target, String http, int duration, int index, boolean endFlg) {
		M3u8Element element = new M3u8Element(target, http, duration, index);
		if(Segmenter.getLimit() != null) {
			// limitが設定されている場合は、m3u8上のデータ量がきまっている。
			elementData.add(element); // エレメントを追加する。
			if(elementData.size() > Segmenter.getLimit()) {
				// elementデータよりサイズが大きい場合は必要のないデータがあるので、先頭のデータを落とす
				M3u8Element removedData = elementData.remove(0);
				if(Segmenter.isStrict()) {
					// いらなくなったファイルは削除する必要があるので、消す
					File deleteFile = new File(removedData.getFile());
					if(deleteFile.exists()) {
						// 削除しておく。
						deleteFile.delete();
					}
				}
			}
			try {
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(Segmenter.getM3u8File(), false)));
				pw.println(header);
				pw.println(allowCache);
				pw.println(targetDuration);
				// 内容を書き込む
				for(M3u8Element data : elementData) {
					pw.println(data.getInfo());
					pw.println(data.getHttp());
				}
				if(endFlg) {
					pw.println("#EXT-X-ENDLIST");
				}
				pw.close();
				pw = null;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else {
			try {
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(Segmenter.getM3u8File(), true)));
				pw.println(element.getInfo());
				pw.println(element.getHttp());
				if(endFlg) {
					pw.println("#EXT-X-ENDLIST");
				}
				pw.close();
				pw = null;
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
