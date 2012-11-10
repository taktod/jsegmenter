package com.ttProject.streaming.data;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * MediaPacketManagerは解析を依頼した場合に与えられたbyteデータからMediaPacketデータを応答すればよい。
 * @author taktod
 */
public interface IMediaPacketManager {
	/**
	 * IURLProtocolHandlerで取得したbyteデータをそのまま突っ込む
	 * 書き込みReadyになったパケットデータを応答します。
	 * @param data
	 */
	public List<IMediaPacket> getPackets(ByteBuffer buffer);
	/**
	 * 現在処理中のデータを取得
	 * @return
	 */
	public IMediaPacket getCurrentPacket();
}
