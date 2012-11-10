package com.ttProject.streaming.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.streaming.mp3.data.Mp3PacketManager;
import com.ttProject.streaming.mpegts.data.HlsPacketManager;

/**
 * mp3とmpegtsを見分けてどちらでコンバートするか判定するPacketManager
 * @author taktod
 *
 */
public class PacketManager implements IMediaPacketManager {
	/** パケットの実データ保持(5バイトまで保持したい) */
	private ByteBuffer buffer;
	/** マネージャーの参照保持 */
	private IMediaPacketManager packetManager = null;
	/** 拡張子 */
	private String ext = null;
	/**
	 * Byteデータを確認して、利用するManagerを自動選択します。
	 */
	@Override
	public List<IMediaPacket> getPackets(byte[] data) {
		// packetManagerがすでに生成済みなら、そっちにデータを受け流す
		if(packetManager != null) {
			return packetManager.getPackets(data);
		}
		if(buffer != null) {
			int length = buffer.remaining() + data.length;
			ByteBuffer newBuffer = ByteBuffer.allocate(length);
			newBuffer.put(buffer);
			buffer = newBuffer;
		}
		else {
			buffer = ByteBuffer.allocate(data.length);
		}
		buffer.put(data);
		buffer.flip();
		// 応答がnullになると呼び出し元でこまることになる。(かならず、listの形で応答することにしている。)
		List<IMediaPacket> result = new ArrayList<IMediaPacket>();
		// 保持データ量が3バイト以上の場合は処理を実施する。
		if(buffer.remaining() > 3) {
			byte[] readByte = new byte[3];
			buffer.get(readByte);
			if(readByte[0] == 0x47) {
				// mpegts
				packetManager = new HlsPacketManager();
				ext = ".ts";
			}
			else if(readByte[0] == -1) {
				// 単純mp3
				packetManager = new Mp3PacketManager();
				ext = ".mp3";
			}
			else if(readByte[0] == 'I'
				&&  readByte[1] == 'D'
				&&  readByte[2] == '3') {
				// 先頭にID3v2タグがはいっているmp3
				packetManager = new Mp3PacketManager();
				ext = ".mp3";
			}
			else {
				throw new RuntimeException("処理不能なファイルに当たりました。");
			}
			// 持っているデータをpacketManagerに渡す。
			buffer.rewind();
			readByte = new byte[buffer.remaining()];
			buffer.get(readByte);
			buffer = null; // もう必要ないので、開放しておく。
			return packetManager.getPackets(readByte);
		}
		return result;
	}
	public String getExt() {
		return ext;
	}
	/**
	 * 現状のパケットを応答しておく。
	 */
	@Override
	public IMediaPacket getCurrentPacket() {
		if(packetManager == null) {
			return null;
		}
		return packetManager.getCurrentPacket();
	}
}

