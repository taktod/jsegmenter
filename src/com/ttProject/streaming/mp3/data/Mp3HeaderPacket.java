package com.ttProject.streaming.mp3.data;

/**
 * mp3の処理で、特殊なヘッダデータはありません。
 * @author taktod
 */
public class Mp3HeaderPacket extends Mp3Packet {
	/**
	 * コンストラクタ
	 * @param manager
	 */
	public Mp3HeaderPacket(Mp3PacketManager manager) {
		super(manager);
	}
	/**
	 * headerパケットであるか応答する。
	 */
	@Override
	public boolean isHeader() {
		return true;
	}
}
