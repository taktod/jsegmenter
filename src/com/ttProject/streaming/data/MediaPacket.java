package com.ttProject.streaming.data;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * MediaPacket動作の共通部分
 * @author taktod
 */
public abstract class MediaPacket implements IMediaPacket {
	/** 保持データ実体 */
	private ByteBuffer buffer;
	private int duration = 0;
	/**
	 * 書き込み用のバッファ参照
	 * TODO この方法では、Bufferが大きくなると、再生成のオーバーヘッドが大きくなってしまいます。
	 * @param size 必要サイズ
	 * @return
	 */
	protected ByteBuffer getBuffer(int size) {
		if(buffer == null) { // なかったら新規作成
			buffer = ByteBuffer.allocate(size);
		}
		if(buffer.remaining() >= size) { // 容量の残りが必要量ある場合はそのまま応答
			return buffer;
		}
		// 必要量ないので、新規にバッファを再生成
		ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() + size); 
		buffer.flip();
		newBuffer.put(buffer);
		buffer = newBuffer;
		return buffer;
	}
	/**
	 * すでに保持しているバッファサイズを参照する。
	 * @return
	 */
	protected int getBufferSize() {
		if(buffer == null) {
			return 0;
		}
		return buffer.position();
	}
	/**
	 * ファイルにデータを書き込む処理
	 */
	@Override
	public void writeData(String targetFile, boolean append) {
		try {
			WritableByteChannel channel = Channels.newChannel(new FileOutputStream(targetFile, append));
			buffer.flip();
			channel.write(buffer);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	protected void setDuration(int duration) {
		this.duration = duration;
	}
	@Override
	public int getDuration() {
		return duration;
	}
}
