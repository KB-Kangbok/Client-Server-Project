/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.server;

import alkagi.client.ClientLoader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author Soreepeong
 */
public class ServerMain extends Thread{
	
	private final ServerSocket mListener;
	private final ArrayList<Client> mPendingClients;
	private final ArrayList<Game> mGames;
	private final Thread mGameMaker;
	
	private ServerMain(int port) throws Exception{
		super();
		mListener = new ServerSocket(port);
		mPendingClients = new ArrayList<>();
		mGames = new ArrayList<>();
		mGameMaker = new Thread(() -> {
			try{
				while(ServerMain.this.isAlive()){
					sleep(1000);
					synchronized (mPendingClients){
						if(mPendingClients.size() >= 2){
							Collections.shuffle(mPendingClients);
							while(mPendingClients.size() >= 2){
								Game g = new Game(mPendingClients.remove(0), mPendingClients.remove(0));
								mGames.add(g);
								g.start();
							}
						}
					}
				}
			}catch(Exception e){
				
			}
		});
	}

	@Override
	public void run() {
		mGameMaker.start();
		System.out.println("Server up @ port 12552 and ready");
		try{
			while(true){
				Socket s = mListener.accept();
				Client c = new Client(s);
				synchronized (mPendingClients){
					mPendingClients.add(c);
				}
				c.start();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws Exception{
		new ServerMain(12552).start();
		ClientLoader.main(args);
	}
	
}
