/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.client;

import alkagi.Ball;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * FXML Controller class
 *
 * @author Soreepeong
 */
public class FXMLClientGameController implements Initializable, ClientCommunicator.GameMessageListener, Ball.OnDeathListener{
	
	@FXML private Canvas mCanvas;
	@FXML private Button mButtonConnect, mButtonStart, mButtonGiveUp;
	@FXML private Label mLabelStatus;
	private GameBoard mGame;
	private Thread mDrawer;
	private ClientCommunicator mCommunicator;
	private String mRemoteHost;
	private double mCursorX, mCursorY;
	private final ArrayList<Flasher> mFlashes = new ArrayList<>();
	
	@Override
	public void initialize(URL url, ResourceBundle rb) {
		Pane root = ((Pane)mCanvas.getParent());
		mCanvas.widthProperty().bind(root.widthProperty());
		mCanvas.heightProperty().bind(root.heightProperty().subtract(32));
		Platform.runLater(() -> onClickConnect(null));
	}
	
	public void setHost(String host){
		mRemoteHost = host;
	}
	
	@FXML private void onClickConnect(ActionEvent e){
		mButtonConnect.setDisable(true);
		new Thread(() -> {
			try {
				Socket s = new Socket(mRemoteHost, 12552);
				mCommunicator = new ClientCommunicator(s, FXMLClientGameController.this);
				mCommunicator.start();
				Platform.runLater(() -> {
					mLabelStatus.setText("Connected, finding an opponent...");
					mButtonGiveUp.setDisable(false);
				});
			} catch (IOException ex) {
				Platform.runLater(() -> {
					mLabelStatus.setText("Connection failed");
					mButtonConnect.setDisable(false);
				});
			}
		}).start();
	}
	
	@FXML private void onClickStart(ActionEvent e){
		mCommunicator.ready();
		mLabelStatus.setText("Wait for the opponent to start...");
		mButtonStart.setDisable(true);
	}
	
	@FXML public void onClickGiveUp(ActionEvent e){
		mCommunicator.giveup();
	}

	@Override
	public void onOpponentFound() {
		mButtonStart.setDisable(false);
		mLabelStatus.setText("Press Start button to start the game.");
	}
	
	@Override
	public void onStart(double width, double height, HashMap<Integer, Ball> balls, HashMap<Integer, Ball> mine) {
		if(mGame != null){
			mCanvas.widthProperty().removeListener(mGame.panLimiterX);
			mCanvas.heightProperty().removeListener(mGame.panLimiterY);
		}
		mGame = new GameBoard(width, height, balls, mine);
		mCanvas.widthProperty().addListener(mGame.panLimiterX);
		mCanvas.heightProperty().addListener(mGame.panLimiterY);
		for(Ball b : balls.values())
			b.listener = this;
		mCanvas.setOnMouseMoved(m -> {
			mCursorX = m.getX();
			mCursorY = m.getY();
			if(!mGame.panning && !mGame.pulling){
				mGame.calculateDragStartPoint(mGame.translateCursorX(m.getX()), mGame.translateCursorY(m.getY()));
			}
		});
		mCanvas.setOnMouseDragged(m -> {
			mCursorX = m.getX();
			mCursorY = m.getY();
			if(mGame.panning){
				mGame.panX = mGame.limitPanX( mGame.panX + mGame.sx - m.getX(), mCanvas.getWidth());
				mGame.panY = mGame.limitPanY( mGame.panY + mGame.sy - m.getY(), mCanvas.getHeight());
				mGame.sx = m.getX();
				mGame.sy = m.getY();
			}else if(mGame.pulling){
				mGame.ex = mGame.translateCursorX(m.getX());
				mGame.ey = mGame.translateCursorY(m.getY());
			}
		});
		mCanvas.setOnMousePressed(m -> {
			if(m.isPrimaryButtonDown() && mGame.exertable && !mGame.animating && mGame.ballExerting != null)
				mGame.pulling = true;
			if(m.isSecondaryButtonDown() && !mGame.animating){
				mGame.panning = true;
				mGame.panX = displayPanX;
				mGame.panY = displayPanY;
				mGame.panScale = displayScale;
				displayPanAutoMoving = false;
				mGame.sx = m.getX();
				mGame.sy = m.getY();
			}
		});
		mCanvas.setOnMouseReleased(m -> {
			if(mGame.pulling && mGame.exertable){
				mGame.pulling = false;
				mCommunicator.exert(mGame.ballExerting, mGame.sx, mGame.sy, (mGame.ex-mGame.sx)*5, (mGame.ey-mGame.sy)*5);
				mGame.ex = mGame.ey = -1;
			}
			if(mGame.panning)
				mGame.panning = false;
			mGame.calculateDragStartPoint(mGame.translateCursorX(m.getX()), mGame.translateCursorY(m.getY()));
		});
		mCanvas.setOnScroll(e -> {
			double x = mGame.translateCursorX(mCursorX);
			double y = mGame.translateCursorY(mCursorY);
			mGame.panScale = Math.min(1, Math.max(0.05, mGame.panScale + e.getDeltaY() / 1000.));
			mGame.panX = mGame.limitPanX(x*mGame.panScale - mCursorX, mCanvas.getWidth());
			mGame.panY = mGame.limitPanY(y*mGame.panScale - mCursorY, mCanvas.getHeight());
			e.consume();
		});
		if(mDrawer == null){
			mDrawer = new Thread(() -> {
				try{
					while(!Thread.interrupted()){
						Platform.runLater(() -> draw());
						Thread.sleep(20);
					}
				}catch(Exception e){}
			});
			mDrawer.start();
		}
		
		double panXsum = 0, panYsum = 0;
		for(Ball b : mGame.mine.values()){
			panXsum += b.x;
			panYsum += b.y;
		}
		panXsum /= mGame.mine.size();
		panYsum /= mGame.mine.size();
		mGame.panScale = displayScale = Math.min(Math.min(1, mCanvas.getWidth() / width), mCanvas.getHeight() / height);
		displayPanX = mGame.panX = mGame.limitPanX(panXsum-mCanvas.getWidth()/2, mCanvas.getWidth());
		displayPanY = mGame.panY = mGame.limitPanY(panYsum-mCanvas.getHeight()/2, mCanvas.getHeight());
		displayPanAutoMoving = false;
		
		mGame.start();
		mButtonStart.setDisable(true);
		mLabelStatus.setText("Game started.");
	}

	@Override
	public void onExerted(int ballId, double sx, double sy, double vx, double vy, HashMap<Integer, Ball> balls) {
		mGame.balls.get(ballId).exert(sx, sy, vx, vy);
		synchronized (mGame){
			mGame.notify();
		}
		new Thread(() -> {
			try{
				synchronized (mGame.animationWaiter){
					mGame.animationWaiter.wait();
					for(Ball b : balls.values())
						mGame.balls.get(b.id).from(b);
				}
			}catch(InterruptedException e){
				
			}
		}).start();
	}

	@Override
	public void onForfeit() {
		mButtonStart.setDisable(true);
		mButtonGiveUp.setDisable(true);
		mButtonConnect.setDisable(false);
		mLabelStatus.setText("The opponent has gave up, or experienced a connection error.");
	}

	@Override
	public void onWin() {
		mButtonStart.setDisable(true);
		mButtonGiveUp.setDisable(true);
		mButtonConnect.setDisable(false);
		mLabelStatus.setText("You win!");
	}

	@Override
	public void onLose() {
		mButtonStart.setDisable(true);
		mButtonGiveUp.setDisable(true);
		mButtonConnect.setDisable(false);
		mLabelStatus.setText("You lose!");
	}

	@Override
	public void onYourTurn() {
		mGame.exertable = true;
		mFlashes.add(new Flasher(255, 255, 255, 500, 500));
		mLabelStatus.setText("Your turn.");
	}

	@Override
	public void onYourTurnExpired() {
		mGame.exertable = false;
		mLabelStatus.setText("Opponent's turn.");
	}

	@Override
	public void onConnectionQuit(boolean errorneous) {
		mCommunicator.interrupt();
		mCommunicator.giveup();
		mGame.interrupt();
		mGame.exertable = false;
	}
	
	private boolean displayPanAutoMoving;
	private double displayPanX, displayPanY, displayScale;
	
	private void draw(){
		GraphicsContext g = mCanvas.getGraphicsContext2D();
		g.save();
		g.setFill(Color.BLACK);
		g.fillRect(0, 0, mCanvas.getWidth(), mCanvas.getHeight());
		for(Iterator<Flasher> i = mFlashes.iterator(); i.hasNext();){
			Flasher f = i.next();
			if(f.done())
				i.remove();
			else{
				g.setFill(f.val());
				g.fillRect(0, 0, mCanvas.getWidth(), mCanvas.getHeight());
			}
		}
		g.setLineWidth(0);
		
		double panXsum = 0, panYsum = 0;
		double panXmin = 0, panXmax = 0, panYmin = 0, panYmax = 0;
		boolean movingInScreen = true;
		int panCount = 0;
		for(Ball b : mGame.balls.values())
			if(b.isAlive() && b.moving()){
				panXsum += b.x;
				panYsum += b.y;
				movingInScreen &= b.x - mGame.panX >= -b.r;
				movingInScreen &= b.x - mGame.panX <= mCanvas.getWidth() + b.r;
				movingInScreen &= b.y - mGame.panY >= -b.r;
				movingInScreen &= b.y - mGame.panY <= mCanvas.getHeight() + b.r;
				panXmin = panCount == 0 ? b.x : Math.min(panXmin, b.x);
				panXmax = panCount == 0 ? b.x : Math.max(panXmax, b.x);
				panYmin = panCount == 0 ? b.y : Math.min(panXmin, b.y);
				panYmax = panCount == 0 ? b.y : Math.max(panXmax, b.y);
				panCount++;
			}
		if(panCount > 0){
			panXsum /= panCount;
			panYsum /= panCount;
			panXsum = mGame.limitPanX(panXsum-mCanvas.getWidth()/2, mCanvas.getWidth());
			panYsum = mGame.limitPanY(panYsum-mCanvas.getHeight()/2, mCanvas.getHeight());
			double scale = mGame.panScale;
			if(panXmax != panXmin) scale = Math.min(scale, mCanvas.getWidth() / (panXmax - panXmin));
			if(panYmax != panYmin) scale = Math.min(scale, mCanvas.getHeight() / (panYmax - panYmin));
			
			if(!displayPanAutoMoving){
				if(movingInScreen){
					displayPanX = mGame.panX;
					displayPanY = mGame.panY;
				}else{
					displayPanX = panXsum;
					displayPanY = panYsum;
				}
				displayScale = mGame.panScale;
				displayPanAutoMoving = true;
			}
			displayPanX = displayPanX + (panXsum - displayPanX) / 10;
			displayPanY = displayPanY + (panYsum - displayPanY) / 10;
			displayScale = displayScale + (scale - displayScale) / 10;
		}else{
			if(displayPanAutoMoving){
				displayPanX = displayPanX + (mGame.panX - displayPanX) / 10;
				displayPanY = displayPanY + (mGame.panY - displayPanY) / 10;
				displayScale = displayScale + (mGame.panScale - displayScale) / 10;
				if(Math.abs(displayPanX - mGame.panX) <= 5 && Math.abs(displayPanY - mGame.panY) <= 5)
					displayPanAutoMoving = false;
			}else{
				displayPanX = mGame.panX;
				displayPanY = mGame.panY;
				displayScale = mGame.panScale;
			}
		}
		g.translate(-displayPanX, -displayPanY);
		g.scale(displayScale, displayScale);
		
		g.setStroke(Color.GRAY);
		g.setLineWidth(1);
		for(int i = 0; i <= 20; i++){
			g.strokeLine(0, mGame.height * i / 20, mGame.width, mGame.height * i / 20);
			g.strokeLine(mGame.width * i / 20, 0, mGame.width * i / 20, mGame.height);
		}
		
		for(Ball b : mGame.balls.values()){
			if(!b.isAlive() && !b.moving())
				continue;
			g.setFill(mGame.mine.containsKey(b.id) ? Color.GREEN : Color.RED);
			g.fillOval(b.x-b.r, b.y-b.r, b.r*2, b.r*2);
		}
		
		if(!mGame.panning && mGame.exertable){
			g.setFill(Color.BLUE);
			g.fillOval(mGame.sx-3, mGame.sy-3, 6, 6);
			if(mGame.pulling){
				g.setLineWidth(3);
				g.setStroke(mGame.ballExerting.exertable(mGame.sx, mGame.sy, (mGame.ex-mGame.sx)*5, (mGame.ey-mGame.sy)*5) ? Color.BLUE : Color.RED);
				g.strokeLine(mGame.sx, mGame.sy, mGame.ex, mGame.ey);
			}
		}
		g.restore();
	}

	@Override
	public void onDeath(Ball b) {
		if(mGame.mine.containsValue(b))
			mFlashes.add(new Flasher(255, 0, 0, 500, 500));
		else
			mFlashes.add(new Flasher(0, 255, 0, 500, 500));
	}
	
	private class Flasher{
		long duration;
		long ends;
		int r, g, b;
		
		public Flasher(int r, int g, int b, long wait, long fade){
			this.r = r; this.g = g; this.b = b;
			duration = fade;
			ends = System.currentTimeMillis() + wait + fade;
		}
		
		Color val(){
			return Color.rgb(r, g, b, Math.min(1,Math.max(0,(double)(ends - System.currentTimeMillis()) / duration)));
		}
		
		boolean done(){
			return ends < System.currentTimeMillis();
		}
	}
}
