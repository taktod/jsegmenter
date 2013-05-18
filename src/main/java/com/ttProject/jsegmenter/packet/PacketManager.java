package com.ttProject.jsegmenter.packet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.packet.IMediaPacket;
import com.ttProject.packet.IMediaPacketManager;
import com.ttProject.packet.MediaPacketManager;
import com.ttProject.packet.mp3.Mp3PacketManager;
import com.ttProject.packet.mpegts.MpegtsPacketManager;

public class PacketManager implements IMediaPacketManager {
	/** パケットの実データ保持(5バイトまで保持したい) */
	private ByteBuffer buffer;
	/** マネージャーの参照保持 */
	private MediaPacketManager packetManager = null;
	/** 拡張子 */
	private String ext = null;
	/** 分割で生成するファイルのデータ長 */
	private final float duration;
	/**
	 * コンストラクタ
	 * @param duration
	 */
	public PacketManager(float duration) {
		this.duration = duration;
	}
	/**
	 * Byteデータを確認して、利用するManagerを自動選択します。
	 */
	public List<IMediaPacket> getPackets(ByteBuffer data) {
		// packetManagerがすでに生成済みなら、そっちにデータを受け流す
		if(packetManager != null) {
			return packetManager.getPackets(data);
		}
		if(buffer != null) {
			int length = buffer.remaining() + data.remaining();
			ByteBuffer newBuffer = ByteBuffer.allocate(length);
			newBuffer.put(buffer);
			buffer = newBuffer;
			buffer.put(data);
			buffer.flip();
		}
		else {
			buffer = data;
		}
		// 応答がnullになると呼び出し元でこまることになる。(かならず、listの形で応答することにしている。)
		List<IMediaPacket> result = new ArrayList<IMediaPacket>();
		// 保持データ量が3バイト以上の場合は処理を実施する。
		if(buffer.remaining() > 3) {
			byte[] readByte = new byte[3];
			buffer.get(readByte);
			if(readByte[0] == 0x47) {
				// mpegts
				packetManager = new MpegtsPacketManager();
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
			packetManager.setDuration(getDuration());
			// 持っているデータをpacketManagerに渡す。
			buffer.rewind();
			return packetManager.getPackets(buffer);
		}
		return result;
	}
	public String getExt() {
		return ext;
	}
	/**
	 * 現状のパケットを応答しておく。
	 */
	public IMediaPacket getCurrentPacket() {
		if(packetManager == null) {
			return null;
		}
		return packetManager.getCurrentPacket();
	}
	public String getHeaderExt() {
		return null;
	}
	public float getDuration() {
		return duration;
	}
}
