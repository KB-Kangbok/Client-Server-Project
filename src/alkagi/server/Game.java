/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.server;

import alkagi.Ball;
import alkagi.DataWriter;
import alkagi.MessageCode;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Soreepeong
 */
public class Game extends Thread{
	private final Client[] mClients;
	private final HashMap<Integer, Ball> mBalls;
	private final double mSizeX, mSizeY;
	private int mTurn;
	private final Object mReadyWaiter = new Object();
	private final Object mExertWaiter = new Object();
	private long mAnimationLength;
	private boolean mErrorStop;
	private boolean mAcceptingExerts;
	
	public Game(Client c1, Client c2){
		super();
		mClients = new Client[]{ c1, c2 };
		mSizeX = mSizeY = 1280;
		mBalls = new HashMap<>();
		for(int i = 160; i<1280; i+=160){
			Ball b = new Ball(i, 120, 16, 160, Ball.newid());
			mBalls.put(b.id, b);
			c1.mMyBalls.put(b.id, b);
		}
		for(int i = 160; i<1280; i+=160){
			Ball b = new Ball(i, 1280-160, 16, 160, Ball.newid());
			mBalls.put(b.id, b);
			c2.mMyBalls.put(b.id, b);
		}
		
		mTurn = (int) Math.round(Math.random());
	}
	
	private long calculateNextState(){
		long time = 0;
		try{
			final HashMap<Ball, Ball> prevState = new HashMap<>();
			while(true){
				final ArrayList<Ball> list = new ArrayList<>(mBalls.values());
				prevState.clear();
				list.stream().forEach(b -> {
					if(b.isAlive())
						prevState.put(b, new Ball(b));
				});
				long dur = 0;
				Ball c1 = null, c2 = null;
				
				for(Ball b : list)
					if(b.isAlive()){
						dur = Math.max(dur, (long) (b.moveTime()*1000));
					}

				for(int i = 0; i < list.size(); i++)
					for(int j = i+1; j < list.size(); j++){
						Ball b1 = list.get(i), b2 = list.get(j);
						if(!b1.isAlive() || !b2.isAlive()) continue;
						long t = (long)(b1.nextcollision(b2)*1000);
						if(t > 0 && dur > t){
							dur = t;
							c1 = b1; c2 = b2;
						}
					}
				time += dur;
				
				for(Ball b : list){
					if(!b.isAlive())
						continue;
					if(b.moving()){
						b.x = prevState.get(b).willX(dur / 1000.);
						b.y = prevState.get(b).willY(dur / 1000.);
						b.vx = prevState.get(b).willVX (dur / 1000.);
						b.vy = prevState.get(b).willVY (dur / 1000.);
					}
					if(b.x < 0 || b.y < 0 || b.x > mSizeX || b.y > mSizeY)
						b.kill();
				}
				
				for(Client c : mClients)
					if(!c.hasAliveBall())
						break;
				
				if(c1 == null || c2 == null)
					break;
				
				c1.collide(c2);
			}
		}catch(Exception e){
			invokeConnectionError();
		}
		for(Ball b : mBalls.values())
			b.vx = b.vy = 0;
		return time;
	}
	
	public void ready(Client client){
		for(Client c : mClients)
			if(!c.isReady())
				return;
		synchronized(mReadyWaiter){
			mReadyWaiter.notify();
		}
	}
	
	public void exert(Client client, int ballId, double sx, double sy, double vx, double vy){
		synchronized(mExertWaiter){
			if(mClients[mTurn] != client) return;
			if(!client.mMyBalls.containsKey(ballId)) return;
			if(!mAcceptingExerts) return;
			if(!mBalls.get(ballId).exertable(sx, sy, vx, vy)) return;
			
                        mAcceptingExerts = false;
			mBalls.get(ballId).exert(sx, sy, vx, vy);
			mAnimationLength = calculateNextState();
			ArrayList<Ball> balls = new ArrayList<>();
			for(Ball b : mBalls.values())
				balls.add(new Ball(b));
			DataWriter stateNotifier = out -> {
				out.writeInt(MessageCode.S2C_EXERTED);
				out.writeInt(ballId);
				out.writeDouble(sx); out.writeDouble(sy); out.writeDouble(vx); out.writeDouble(vy);
				for(Ball b : balls){
					out.writeBoolean(true);
					b.write(out);
				}
				out.writeBoolean(false);
			};
			for(Client c : mClients)
				c.sender.send(stateNotifier);
			mExertWaiter.notify();
		}
	}
	
	public void invokeConnectionError(){
		mErrorStop = true;
		interrupt();
	}

	@Override
	public void run() {
		DataWriter writer;
		try{
			writer = out -> out.writeInt(MessageCode.S2C_FOUND_OPPONENT);
			for(Client c : mClients){
				c.setGame(this);
				c.sender.send(writer);
			}
			synchronized(mReadyWaiter){
				mReadyWaiter.wait();
			}
			writer = out -> {
				out.writeInt(MessageCode.S2C_START);
				out.writeDouble(mSizeX);
				out.writeDouble(mSizeY);
				for(Client c : mClients)
					for(Ball b : c.mMyBalls.values()){
						out.writeInt(c.is(out) ? 1 : 2);
						b.write(out);
					}
				out.writeInt(0);
			};
			for(Client c : mClients)
				c.sender.send(writer);
			mClients[mTurn].sender.send(out -> out.writeInt(MessageCode.S2C_YOUR_TURN));
			boolean inter;
			game: while(!(inter=interrupted())){
				for(Client c : mClients)
					if(!c.hasAliveBall())
						break game;
				mAcceptingExerts = true;
				synchronized(mExertWaiter){
					mExertWaiter.wait(30000);
				}
				mAcceptingExerts = false;
				mClients[mTurn].sender.send(out -> out.writeInt(MessageCode.S2C_YOUR_TURN_EXPIRED));
				sleep(mAnimationLength);
				mTurn = 1 - mTurn;
				mClients[mTurn].sender.send(out -> out.writeInt(MessageCode.S2C_YOUR_TURN));
			}
			if(inter)
				throw new InterruptedException();
		}catch(InterruptedException e){
			invokeConnectionError();
		}
		if(mErrorStop){
			writer = out -> out.writeInt(MessageCode.S2C_FORFEIT);
		}else{
			writer = out -> {
				for(Client c : mClients)
					if(c.is(out)){
						if(c.hasAliveBall())
							out.writeInt(MessageCode.S2C_WIN);
						else
							out.writeInt(MessageCode.S2C_LOSE);
					}
			};
		}
		for(Client c : mClients){
			c.sender.send(writer);
			c.sender.send(DataWriter.STOP);
		}
	}
}
