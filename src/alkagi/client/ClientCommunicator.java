/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.client;

import alkagi.Ball;
import alkagi.DataWriter;
import alkagi.MessageCode;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import javafx.application.Platform;

/**
 *
 * @author Soreepeong
 */
public class ClientCommunicator extends Thread implements DataWriter.OnDataWriterQuitListener{
	
	private final Socket mSocket;
	private final DataInputStream mIn;
	private final DataOutputStream mOut;
	private final GameMessageListener mListener;
	private boolean mGameSet;
	
	public final DataWriter.DataWriterThread sender;
	
	public ClientCommunicator(Socket s, GameMessageListener listener) throws IOException{
		super();
		mSocket = s;
		mListener = listener;
		mIn = new DataInputStream(s.getInputStream());
		mOut = new DataOutputStream(s.getOutputStream());
		sender = new DataWriter.DataWriterThread(mOut, this);
	}

	@Override
	public void run() {
		sender.start();
		try{
			while(true){
				switch(mIn.readInt()){
					case MessageCode.S2C_FOUND_OPPONENT:{
						Platform.runLater(() -> mListener.onOpponentFound());
						break;
					}
					case MessageCode.S2C_START:{
						double w = mIn.readDouble();
						double h = mIn.readDouble();
						HashMap<Integer, Ball> balls = new HashMap<>();
						HashMap<Integer, Ball> mine = new HashMap<>();
						int type;
						while(0 != (type = mIn.readInt())){
							Ball b = new Ball(mIn);
							balls.put(b.id, b);
							if(type == 1)
								mine.put(b.id, b);
						}
						Platform.runLater(() -> mListener.onStart(w, h, balls, mine));
						break;
					}
					case MessageCode.S2C_EXERTED:{
						int id = mIn.readInt();
						double sx = mIn.readDouble();
						double sy = mIn.readDouble();
						double vx = mIn.readDouble();
						double vy = mIn.readDouble();
						HashMap<Integer, Ball> balls = new HashMap<>();
						while(mIn.readBoolean()){
							Ball b = new Ball(mIn);
							balls.put(b.id, b);
						}
						Platform.runLater(() -> mListener.onExerted(id, sx, sy, vx, vy, balls));
						break;
					}
					case MessageCode.S2C_FORFEIT:{
						mGameSet = true;
						Platform.runLater(() -> mListener.onForfeit());
						break;
					}
					case MessageCode.S2C_WIN:{
						mGameSet = true;
						Platform.runLater(() -> mListener.onWin());
						break;
					}
					case MessageCode.S2C_LOSE:{
						mGameSet = true;
						Platform.runLater(() -> mListener.onLose());
						break;
					}
					case MessageCode.S2C_YOUR_TURN:{
						Platform.runLater(() -> mListener.onYourTurn());
						break;
					}
					case MessageCode.S2C_YOUR_TURN_EXPIRED:{
						Platform.runLater(() -> mListener.onYourTurnExpired());
						break;
					}
				}
			}
		}catch(IOException io){
		}
		sender.interrupt();
	}
	
	public void giveup(){
		sender.send(DataWriter.STOP);
		try{ mSocket.close(); }catch(Exception e){}
		try{ mIn.close(); }catch(Exception e){}
		try{ mOut.close(); }catch(Exception e){}
		if(!mGameSet){
			mGameSet = true;
			Platform.runLater(() -> mListener.onForfeit());
		}
	}
	
	public void ready(){
		sender.send(out -> out.writeInt(MessageCode.C2S_READY));
	}
	
	public void exert(Ball b, double sx, double sy, double vx, double vy){
		sender.send(out -> {
			out.writeInt(MessageCode.C2S_EXERT);
			out.writeInt(b.id);
			out.writeDouble(sx);
			out.writeDouble(sy);
			out.writeDouble(vx);
			out.writeDouble(vy);
		});
	}

	@Override
	public void onDataWriterQuit(boolean errorneous) {
		mListener.onConnectionQuit(errorneous);
	}
	
	public interface GameMessageListener{
		void onOpponentFound();
		void onStart(double width, double height, HashMap<Integer, Ball> balls, HashMap<Integer, Ball> mine);
		void onExerted(int ballId, double sx, double sy, double vx, double vy, HashMap<Integer, Ball> balls);
		void onForfeit();
		void onWin();
		void onLose();
		void onYourTurn();
		void onYourTurnExpired();
		void onConnectionQuit(boolean errorneous);
	}
}
