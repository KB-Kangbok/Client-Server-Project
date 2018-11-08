/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.server;

import alkagi.Ball;
import alkagi.DataWriter;
import alkagi.DataWriter.DataWriterThread;
import alkagi.MessageCode;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

/**
 *
 * @author Soreepeong
 */
public class Client extends Thread implements DataWriter.OnDataWriterQuitListener{
	private final Socket mSocket;
	private final DataInputStream mIn;
	private final DataOutputStream mOut;
	public final DataWriterThread sender;
	public final HashMap<Integer, Ball> mMyBalls;
	private Game mGame;
	private boolean mConnected;
	private boolean mReady;
	
	public Client(Socket socket) throws IOException{
		super();
		mSocket = socket;
		mIn = new DataInputStream(socket.getInputStream());
		mOut = new DataOutputStream(socket.getOutputStream());
		mMyBalls = new HashMap<>();
		sender = new DataWriterThread(mOut, this);
	}
	
	public boolean is(DataOutputStream out){
		return out == mOut;
	}
	
	public boolean isReady(){
		return mReady;
	}
	
	public boolean hasAliveBall(){
		return mMyBalls.values().stream().anyMatch((b) -> (b.isAlive()));
	}
	
	public void setGame(Game g){
		mGame = g;
	}

	@Override
	public void run() {
		sender.start();
		try{
			while(true){
				switch(mIn.readInt()){
					case MessageCode.C2S_READY:{
						if(mGame != null){
							mReady = true;
							mGame.ready(this);
						}
						break;
					}
					case MessageCode.C2S_EXERT:{
						if(mGame != null)
							mGame.exert(this, mIn.readInt(), mIn.readDouble(), mIn.readDouble(), mIn.readDouble(), mIn.readDouble());
						break;
					}
				}
			}
		}catch(IOException io){
		}
		mConnected = false;
		mGame.invokeConnectionError();
		sender.interrupt();
	}

	@Override
	public void onDataWriterQuit(boolean errorneous) {
		if(errorneous)
			mGame.invokeConnectionError();
		mConnected = false;
		Client.this.interrupt();
		try{
			mSocket.close();
		}catch(Exception e){}
	}
}
