/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author Soreepeong
 */
public interface DataWriter {
	public static DataWriter STOP = out -> {};
	void writeData(DataOutputStream out) throws IOException;
	
	public class DataWriterThread extends Thread{
		private final ArrayList<DataWriter> mDataWriterQueue;
		private final OnDataWriterQuitListener mListener;
		private final DataOutputStream mOut;
		
		public DataWriterThread(DataOutputStream out, OnDataWriterQuitListener listener){
			super();
			mListener = listener;
			mDataWriterQueue = new ArrayList<>();
			mOut = out;
		}
		
		public void send(DataWriter wr){
			synchronized(mDataWriterQueue){
				mDataWriterQueue.add(wr);
				mDataWriterQueue.notify();
			}
		}

		@Override
		public void run() {
			boolean error = false;
			try{
				loop: while(!Thread.interrupted()){
					final ArrayList<DataWriter> mWriters;
					synchronized(mDataWriterQueue){
						if(mDataWriterQueue.isEmpty())
							mDataWriterQueue.wait();
						mWriters = new ArrayList(mDataWriterQueue);
						mDataWriterQueue.clear();
					}
					for(DataWriter wr : mWriters){
						if(wr == DataWriter.STOP)
							break loop;
						wr.writeData(mOut);
					}
				}
			}catch(InterruptedException | IOException e){
				error = true;
			}
			mListener.onDataWriterQuit(error);
		}
		
	}
	
	public interface OnDataWriterQuitListener{
		void onDataWriterQuit(boolean errorneous);
	}
}
