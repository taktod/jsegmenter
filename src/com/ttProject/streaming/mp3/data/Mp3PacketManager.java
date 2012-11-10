package com.ttProject.streaming.mp3.data;

import java.nio.ByteBuffer;

import com.ttProject.streaming.data.IMediaPacket;
import com.ttProject.streaming.data.MediaPacketManager;

/**
 * Mp3のHttpLiveStreamingのパケットを管理するマネージャー
 * @author taktod
 */
public class Mp3PacketManager extends MediaPacketManager {
	/** 経過フレーム数 */
	private int frameCount = 0;
	/**
	 * 経過フレーム数を取得する
	 * @return
	 */
	public int getFrameCount() {
		return frameCount;
	}
	/**
	 * 経過フレーム数をインクリメントする。
	 */
	public void addFrameCount() {
		this.frameCount ++;
	}
	/**
	 * パケットの内容を解析する
	 */
	@Override
	protected IMediaPacket analizePacket(ByteBuffer buffer) {
		IMediaPacket packet = getCurrentPacket();
		if(packet == null) {
			packet = new Mp3MediaPacket(this); // mp3にはheaderパケットが存在しないので、headerは無視
		}
		if(packet.analize(buffer)) {
			setCurrentPacket(null);
			return packet;
		}
		else {
			setCurrentPacket(packet);
			return null;
		}
	}
}
