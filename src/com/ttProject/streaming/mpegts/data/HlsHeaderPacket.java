package com.ttProject.streaming.mpegts.data;

import java.nio.ByteBuffer;

/**
 * httpLiveStreamingで利用するヘッダ情報パケット
 * @author taktod
 */
public class HlsHeaderPacket extends HlsPacket {
	/** 処理Manager */
	private final HlsPacketManager manager;
	/**
	 * コンストラクタ
	 * @param manager
	 */
	public HlsHeaderPacket(HlsPacketManager manager) {
		super(manager);
		this.manager = manager;
	}
	/**
	 * ヘッダーであるか応答する。
	 */
	@Override
	public boolean isHeader() {
		return true;
	}
	/**
	 * 内容の解析を実施する
	 */
	@Override
	public boolean analize(ByteBuffer buffer) {
		// header処理
		while(buffer.remaining() >= 188) {
			boolean isPmtChecked = false;
			int pid = getPid(buffer);
			// pmtがくるまで取得しなければいけない。
			if(pid == HlsPacketManager.PATId) {
				analizePat(buffer);
			}
			else if(manager.isPmtId(pid)) {
				analizePmt(buffer);
				isPmtChecked = true;
			}
			// 188バイトのデータを追記します。
			byte[] data = new byte[188];
			buffer.get(data);
			getBuffer(188).put(data);
			if(isPmtChecked) { // pmtの解析がおわっている場合は処理完了済み
				return true;
			}
		}
		// データが足りなくておわった。
		return false;
	}
}
