package com.ttProject.streaming.mpegts.data;

import java.nio.ByteBuffer;

import com.ttProject.Segmenter;

/**
 * メディアデータ用のパケット
 * @author taktod
 */
public class HlsMediaPacket extends HlsPacket {
	private final HlsPacketManager manager;
	private long startTic;
	public HlsMediaPacket(HlsPacketManager manager) {
		super(manager);
		this.manager = manager;
		startTic = manager.getPassedTic();
	}
	@Override
	public boolean isHeader() {
		return false;
	}
	@Override
	public boolean analize(ByteBuffer buffer) {
		while(buffer.remaining() >= 188) {
			int pid = getPid(buffer);
			if(manager.isPcrId(pid)) {
				// mediaIDだったら解析にまわす
				analizePcrPacket(buffer);
				// PCRであるかの確認を実施し、PCRなら、その分割可能か確認する。
				// isSplittablePacketでは同じ処理を複数やってるみたいな感じになっているので、いつかなおしておきたい。
				if(isSplittablePacket(buffer) && getBufferSize() > 0) {
					// バッファがある状態でキーパケットがきたら。次のパケットに進む。
					return true;
				}
			}
			// 188バイトのデータを追記します。
			byte[] data = new byte[188];
			buffer.get(data);
			getBuffer(188).put(data);
		}
		// パケットが不足したはず。
		return false;
	}
	/**
	 * pcrパケットを解析します。みつけた時間は現在時刻として登録しておきます
	 * @param buffer
	 * @return
	 */
	private void analizePcrPacket(ByteBuffer buffer) {
		int position = buffer.position();
		byte[] header = new byte[4];
		buffer.get(header);
		// syncByte
		if(header[0] != 0x47) {
			throw new RuntimeException("syncByteがおかしいです。");
		}
		int adaptationFlg = (header[3] & 0x20) >>> 5;
		if(adaptationFlg == 1) { // adaptationFlgがたっている場合は、追加情報に時間情報があるかもしれない
			// adaptationFieldについて解析する。
			int adaptationFieldLength = (buffer.get() & 0xFF);
			if(adaptationFieldLength != 0) {
				byte[] data = new byte[adaptationFieldLength];
				int pos = 0;
				buffer.get(data);
				int pcrFlg = (data[pos] & 0x10) >>> 4;
				if(pcrFlg == 1) { // pcrフラグがたっている場合は、時間の情報がある
					// 以下33ビット読み込んでデータをとる
					// 6バイト読み込む
					long tic = (data[++pos] & 0xFF);
					tic = (tic << 8) + (data[++pos] & 0xFF);
					tic = (tic << 8) + (data[++pos] & 0xFF);
					tic = (tic << 8) + (data[++pos] & 0xFF);
					tic = (tic << 1) + ((data[++pos] & 0x80) >>> 7);
					manager.setTimeTic(tic);
					++pos;
					// durationを更新
					setDuration((int)(manager.getPassedTic() / 90000) - manager.getPassedTime());
				}
			}
		}
		// 元に戻して次にまわす。
		buffer.position(position);
	}
	/**
	 * 分割可能なmpegtsファイルであるか確認する。
	 * @param buffer
	 * @return
	 */
	private boolean isSplittablePacket(ByteBuffer buffer) {
		int position = buffer.position();
		int pid;
		byte[] header = new byte[4];
		buffer.get(header);
		// syncByte
		if(header[0] != 0x47) {
			throw new RuntimeException("syncByteがおかしいです。");
		}
		pid = ((header[1] & 0x1F) << 8) + (header[2] & 0xFF);
		int adaptationFlg = (header[3] & 0x20) >>> 5;
		if(adaptationFlg == 1) { // adaptationFlgがたっている場合は、追加情報に時間情報があるかもしれない
			// adaptationFieldについて解析する。
			int adaptationFieldLength = (buffer.get() & 0xFF);
			if(adaptationFieldLength != 0) {
				byte[] data = new byte[adaptationFieldLength];
				int pos = 0;
				buffer.get(data);
				int randomAccessIndicator = (data[pos] & 0x40) >>> 6;
				int pcrFlg = (data[pos] & 0x10) >>> 4;
				boolean isH264 = manager.isH264Id(pid);
				if(pcrFlg == 1) { // pcrフラグがたっている場合は、時間の情報がある
					if(isH264 && randomAccessIndicator == 0) {
						// h.264の場合はrandomAccessIndicatorを調べる必要がある。
						buffer.position(position);
						return false;
					}
					// adaptationField後のデータを読みこんでPESパケットヘッダであるか確認する。
					data = new byte[3];
					buffer.get(data);
					// メディアトラックであることを確認
					if(data[0] == 0x00 && data[1] == 0x00 && data[2] == 0x01) {
						// 経過時間を取得
						int passedTime = (int)((manager.getPassedTic() - startTic) / 90000);
						if(passedTime > Segmenter.getDuration()) {
							// 経過時間が分割秒数を超えている場合は、次のパケットにすすむ
							manager.addPassedTime(getDuration());
							buffer.position(position);
							return true;
						}
					}
				}
			}
		}
		// 元に戻して次にまわす。
		buffer.position(position);
		return false;
	}
}
