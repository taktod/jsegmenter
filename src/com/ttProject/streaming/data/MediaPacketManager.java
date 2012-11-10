package com.ttProject.streaming.data;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaPacketManager動作の共通部分
 * @author taktod
 */
public abstract class MediaPacketManager implements IMediaPacketManager {
	/** 保持データ実体 */
	private ByteBuffer buffer = null;
	/** 現在処理中のパケット参照 */
	private IMediaPacket currentPacket = null;
	/** すでにm3u8上に書き込みした経過時間 */
	private int passedTime = 0;
	/**
	 * パケットの解析処理
	 */
	@Override
	public List<IMediaPacket> getPackets(ByteBuffer data) {
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
		List<IMediaPacket> result = new ArrayList<IMediaPacket>();
		while(buffer.remaining() > 0) {
			IMediaPacket packet = analizePacket(buffer);
			if(packet == null) {
				break;
			}
			else {
				result.add(packet);
			}
		}
		return result;
	}
	/**
	 * パケットの中身を解析する。
	 * @param buffer
	 * @return
	 */
	protected abstract IMediaPacket analizePacket(ByteBuffer buffer);
	/**
	 * 現在処理中のパケットを取得
	 */
	@Override
	public IMediaPacket getCurrentPacket() {
		return currentPacket;
	}
	/**
	 * 処理中のパケットの更新
	 * @param packet
	 */
	protected void setCurrentPacket(IMediaPacket packet) {
		currentPacket = packet;
	}
	/**
	 * 経過秒数を増やす
	 * @param time
	 */
	public void addPassedTime(int time) {
		passedTime += time;
	}
	/**
	 * 経過秒数を取得する
	 * @return
	 */
	public int getPassedTime() {
		return passedTime;
	}
}
