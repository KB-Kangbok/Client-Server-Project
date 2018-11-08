/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi;

/**
 *
 * @author Soreepeong
 */
public class MessageCode {
	
	public static final int C2S_READY = 1;
	public static final int C2S_EXERT = 2;
	
	public static final int S2C_FOUND_OPPONENT = 1;
	public static final int S2C_START = 2;
	public static final int S2C_EXERTED = 3;
	public static final int S2C_FORFEIT = 4;
	public static final int S2C_WIN = 5;
	public static final int S2C_LOSE = 6;
	public static final int S2C_YOUR_TURN = 7;
	public static final int S2C_YOUR_TURN_EXPIRED = 8;
	
	private MessageCode() {}
}
