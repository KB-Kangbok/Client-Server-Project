/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.client;

import alkagi.Ball;
import java.util.ArrayList;
import java.util.HashMap;
import javafx.beans.value.ChangeListener;

/**
 *
 * @author Soreepeong
 */
public class GameBoard extends Thread{
	public final double width;
	public final double height;
	public final HashMap<Integer, Ball> balls;
	public final HashMap<Integer, Ball> mine;
	
	public double panX, panY, panScale;
	
	public double sx, sy, ex, ey;
	public Ball ballExerting;
	
	public boolean panning, animating, pulling;
	
	public boolean exertable;
	
	public final Object animationWaiter = new Object();
	
	public final ChangeListener<Number> panLimiterX = (ob, ov, nv) -> panX = limitPanX(panX, nv.doubleValue());
	public final ChangeListener<Number> panLimiterY = (ob, ov, nv) -> panY = limitPanY(panY, nv.doubleValue());
	
	public GameBoard(double w, double h, HashMap<Integer, Ball> b, HashMap<Integer, Ball> m){
		super();
		width = w; height = h; balls = b; mine = m;
	}
	
	public double limitPanX(double x, double cwidth){
		double r = 0;
		for(Ball b : balls.values())
			r = Math.max(r, b.r);
		r *= 3;
		if(cwidth >= width * panScale + r)
			return -(cwidth - width * panScale) / 2;
		if(x < -r)
			return -r;
		else if(x > width * panScale + r - cwidth)
			return width * panScale + r - cwidth;
		return x;
	}
	
	public double limitPanY(double y, double cheight){
		double r = 0;
		for(Ball b : balls.values())
			r = Math.max(r, b.r);
		r *= 3;
		if(cheight >= height * panScale + r)
			return -(cheight - height * panScale) / 2;
		if(y < -r)
			return -r;
		else if(y > height * panScale + r - cheight)
			return height * panScale + r - cheight;
		return y;
	}
	
	public double translateCursorX(double x){
		return (x + panX) / panScale;
	}
	
	public double translateCursorY(double y){
		return (y + panY) / panScale;
	}
	
	public void calculateDragStartPoint(double x, double y){
		ex = sx = x;
		ey = sy = y;
		ballExerting = null;
		for(Ball b : mine.values())
			if(b.isAlive() && (ballExerting == null || b.dist(x, y) <= ballExerting.dist(x, y)))
				ballExerting = b;
		if(ballExerting == null)
			return;
		if(ballExerting.y == y)
			sx = ballExerting.x + ballExerting.r * (ballExerting.x < x ? 1 : -1);
		else if(ballExerting.x == x)
			sy = ballExerting.y + ballExerting.r * (ballExerting.y < y ? 1 : -1);
		else{
			double k = Math.atan((ballExerting.y - y) / (ballExerting.x - x));
			sx = ballExerting.x + ballExerting.r * Math.cos(k) * (x > ballExerting.x ? 1 : -1);
			sy = ballExerting.y + ballExerting.r * Math.sin(k) * (x > ballExerting.x ? 1 : -1);
		}
	}

	@Override
	public void run() {
		HashMap<Ball, Ball> prevState = new HashMap<>();
		boolean dowait = true;
		while(!Thread.interrupted()){
			try{
				final ArrayList<Ball> list;
				synchronized (this){
					if(dowait){
						synchronized (animationWaiter){
							animationWaiter.notify();
							animating = false;
						}
						this.wait();
						animating = true;
					}
					dowait = true;
					list = new ArrayList<>(balls.values());
				}
				prevState.clear();
				for(Ball b : list)
					prevState.put(b, new Ball(b));
				boolean moving = true;
				long now = System.currentTimeMillis();
				long dur = 0;
				long d = 0;
				Ball c1 = null, c2 = null;
				
				for(Ball b : list)
					if(b.isAlive())
						dur = Math.max(dur, (long) (b.moveTime()*1000));


				for(int i = 0; i < list.size(); i++)
					for(int j = i+1; j < list.size(); j++){
						Ball b1 = list.get(i), b2 = list.get(j);
						if(!b1.isAlive() || !b2.isAlive()) continue;
						long t = (long)(b1.nextcollision(b2)*1000);
						if(t > 0 && (dur == -1 || dur > t)){
							dur = t;
							c1 = b1; c2 = b2;
						}
					}


				while(moving && d != dur){
					moving = false;
					d = System.currentTimeMillis() - now;
					if(d > dur && dur != -1) d = dur;
					for(Ball b : list){
						if(b.moving()){
							b.x = prevState.get(b).willX(d / 1000.);
							b.y = prevState.get(b).willY(d / 1000.);
							b.vx = prevState.get(b).willVX (d / 1000.);
							b.vy = prevState.get(b).willVY (d / 1000.);
							moving = true;
						}
						if(b.x < 0 || b.y < 0 || b.x > width || b.y > height)
							b.kill();
					}
				}

				if(dur > 0 && c1 != null && c2 != null){
					c1.collide(c2);
					dowait = false;
				}
			}catch(InterruptedException e){
			}
		}
	}
	
}
