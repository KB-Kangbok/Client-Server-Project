/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 *
 * @author Soreepeong
 */
public class Ball {
	private boolean alive;
	public double x;
	public double y;
	public double vx;
	public double vy;
	public double r;
	public OnDeathListener listener;
	public final double f;
	public final double g = 9.8;
	public final int id;
	private static volatile int id_=0;
	
	public static synchronized int newid(){ return id_++; }
	
	public Ball(double _x, double _y, double _r, double _f, int _id){
		x=_x; y=_y; r=_r; f=_f; id=_id; alive = true;
	}
	
	public Ball(Ball b){
		x = b.x; y = b.y; vx = b.vx; vy = b.vy; r = b.r; f = b.f; id = b.id; alive = b.alive;
	}
	
	public Ball(DataInputStream in) throws IOException{
		alive = in.readBoolean();
		x = in.readDouble(); y = in.readDouble();
		r = in.readDouble(); f = in.readDouble();
		id = in.readInt();
	}
	
	public void write(DataOutputStream out) throws IOException{
		out.writeBoolean(alive);
		out.writeDouble(x); out.writeDouble(y);
		out.writeDouble(r); out.writeDouble(f);
		out.writeInt(id);
	}
	
	public void from(Ball b){
		x = b.x; y = b.y; vx = b.vx; vy = b.vy; r = b.r;
		if(alive && !b.alive){
			if(listener != null)
				listener.onDeath(this);
			alive = false;
		}else
			alive = b.alive;
	}

	@Override
	public int hashCode() { return id; }

	@Override public boolean equals(Object obj) { return obj instanceof Ball && id == ((Ball) obj).id; }
	
	public double dist(double _x, double _y){ return Math.sqrt(Math.pow(x-_x,2)+Math.pow(y-_y,2)); }
	public double dist(Ball b){ return Math.sqrt(Math.pow(x-b.x,2)+Math.pow(y-b.y,2)); }
	public double distAt(Ball b, double t){ return Math.sqrt(Math.pow(willX(t) - b.willX(t),2) + Math.pow(willY(t) - b.willY(t),2)); }
	
	public boolean moving(){ return Math.abs(vx) >= 0.1 || Math.abs(vy) >= 0.1; }
	
	public double moveTime(){ return Math.sqrt(vx*vx+vy*vy)/g/f/2; }
	public double moveTime(double t){ return Math.min(t, Math.sqrt(vx*vx+vy*vy)/g/f/2); }
	
	public double willX(double t){ t = moveTime(t); return x - (vx == 0 ? 0 : vx * t - 0.5 * g * f * t * t * Math.cos(Math.atan(vy/vx)) * (vx>0 ? 1 : -1)); }
	public double willX(){ return x - (vx == 0 ? 0 : vx * vx * 0.5 / g / f / Math.cos(Math.atan(vy/vx)) * (vx>0 ? 1 : -1)); }
	
	public double willY(double t){ t = moveTime(t); return y - (vy == 0 ? 0 : vy * t - 0.5 * g * f * t * t * (vx==0?1:Math.sin(Math.atan(vy/vx)) * (vx>0 ? 1 : -1))); }
	public double willY(){ return y - (vy == 0 ? 0 : vy * vy * 0.5 / g / f / Math.sin(Math.atan(vy/vx)) * (vx>0 ? 1 : -1)); }
	
	public double willVX(double t){ t = moveTime(t); return vx == 0 || Math.abs(vx) < 2 * g * f * t * Math.cos(Math.atan(vy/vx)) ? 0 : vx - g * f * t * Math.cos(Math.atan(vy/vx)) * (vx>0 ? 1 : -1); }
	public double willVY(double t){ t = moveTime(t); return vy == 0 || Math.abs(vy) < Math.abs(2 * g * f * t * Math.sin(Math.atan(vy/vx))) ? 0 : vy - g * f * t * Math.cos(Math.atan(vy/vx)) * (vx>0 ? 1 : -1); }
	
	public double nextcollision(Ball next){
		// pass 1: find the minimum value of distance between two balls (the graph will have one minimum) when the center collides
		double tmin = 0, tmax = Math.max(Math.sqrt(vx*vx+vy*vy)/f/g,Math.sqrt(next.vx*next.vx+next.vy*next.vy)/next.f/next.g);
		for(int i = 0; i < 128 && tmin != tmax; i++){
			double t1 = tmin + (tmax - tmin) / 4;
			double t2 = tmin + (tmax - tmin) / 4 * 3;
			double dist1 = distAt(next, t1);
			double dist2 = distAt(next, t2);
			if(dist1 < dist2)
				tmax = t2;
			else
				tmin = t1;
		}
		// pass 2
		tmax = (tmin + tmax) / 2; tmin = 0;
		for(int i = 0; i < 128 && tmin != tmax; i++){
			double t1 = tmin + (tmax - tmin) / 4;
			double t2 = tmin + (tmax - tmin) / 4 * 3;
			double dist1 = distAt(next, t1);
			double dist2 = distAt(next, t2);
			if(Math.abs(dist1 - r - next.r) < Math.abs(dist2 - r - next.r))
				tmax = t2;
			else
				tmin = t1;
		}
		double t = (tmax + tmin) / 2;
		double dist = distAt(next, t);
		if(dist <= r + next.r + 1)
			return t;
		else
			return -1;
	}
	
	public void collide(Ball next){
		double theta = Math.atan2 (next.y - y, next.x - x);
		double v1 = vx * Math.cos(theta) + vy * Math.sin(theta);
		double v2 = next.vx * Math.cos(theta) + next.vy * Math.sin(theta);
		double c = 0.95;
		double v_1 = ((1+c) * v1 + (1-c) * v2)/2;
		double v_2 = ((1+c) * v2 + (1-c) * v1)/2;
		vx += Math.cos(theta) * (v_2 - v_1);
		vy += Math.sin(theta) * (v_2 - v_1);
		next.vx += Math.cos(theta) * (v_1 - v_2);
		next.vy += Math.sin(theta) * (v_1 - v_2);
	}
	
	public boolean exertable(double x, double y, double vx, double vy){
		// acos( (x1x2 + y1y2) / v1v2)
		// b.vx = vx; b.vy = vy;
		if(dist(x,y) == 0 || (vx == 0 && vy == 0)) return false;
		double rad = ((x - this.x) * vx + (y - this.y) * vy) / this.dist(x, y) / Math.sqrt(vx*vx+vy*vy);
		if(rad > 1) rad = 1;
		if(rad < -1) rad = -1;
		rad = Math.acos(rad);
		if(vy < 0) rad = Math.PI * 2 - rad;
		return rad < Math.PI / 2 || rad > Math.PI * 3 / 2;
	}
	
	public void exert(double x, double y, double vx, double vy){
		// acos( (x1x2 + y1y2) / v1v2)
		// b.vx = vx; b.vy = vy;
		if(this.dist(x,y) == 0 || (vx == 0 && vy == 0)) return;
		double rad = ((x - this.x) * vx + (y - this.y) * vy) / this.dist(x, y) / Math.sqrt(vx*vx+vy*vy);
		if(rad > 1) rad = 1;
		if(rad < -1) rad = -1;
		rad = Math.acos(rad);
		if(vy < 0) rad = Math.PI * 2 - rad;
		if(rad >= Math.PI / 2 && rad <= Math.PI * 3 / 2)
			return;
		double v = Math.cos(rad) * Math.sqrt(vx*vx+vy*vy);
		this.vx = v * (this.x==x ? 0 : Math.cos(Math.atan((y-this.y)/(x-this.x))) * (x>this.x ? 1 : -1));
		this.vy = v * (this.x==x ? (y>this.y?1:-1) : Math.sin(Math.atan((y-this.y)/(x-this.x))) * (x>this.x ? 1 : -1));
	}
	
	public boolean isAlive(){
		return alive;
	}
	
	public void kill(){
		if(!alive)
			return;
		alive = false;
		if(listener != null)
			listener.onDeath(this);
	}
	
	public interface OnDeathListener{
		void onDeath(Ball b);
	}
}