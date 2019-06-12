package com.ifabula.fingerprintplugin;
/**
 * The utility class for process the WSQ image
 */
public class wsq {
	private static wsq mCom=null;
	
	public static wsq getInstance(){
		if (mCom == null){
			mCom=new wsq();
		}
		return mCom;
	}

	/**
	 * Convert the raw image into WSQ format
	 * @param inpdata The raw image data in byte array
	 * @param inpsize The size of the raw image data
	 * @param width The width of the image
	 * @param height The height of the image
	 * @param outdata The output WSQ image in byte array
	 * @param outsize The size of output
	 * @param bitrate The compress ratio of WSQ
	 * @return
	 */
	public native int RawToWsq(byte[] inpdata,int inpsize,int width,int height,byte[] outdata,int[] outsize,float bitrate);

	/**
	 * Convert the WSQ image into RAW format
	 * @param inpdata The WSQ image in byte array
	 * @param inpsize The size of input
	 * @param outdata The output RAW image in byte array
	 * @param outsize The size of output
	 * @return
	 */
	public native int WsqToRaw(byte[] inpdata,int inpsize,byte[] outdata,int[] outsize);
	
	static {
		System.loadLibrary("wsq");
	}
}