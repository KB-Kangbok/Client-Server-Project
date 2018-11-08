/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package alkagi.client;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 *
 * @author Soreepeong
 */
public class ClientLoader extends Application {
	
	@Override
	public void start(Stage primaryStage) {
		TextField ip = new TextField("127.0.0.1");
		StringBuilder iplist = new StringBuilder("My IPs:");
		try{
			for(Enumeration e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
				NetworkInterface n = (NetworkInterface) e.nextElement();
				for(Enumeration ee = n.getInetAddresses(); ee.hasMoreElements(); ) {
					InetAddress inet = ((InetAddress) ee.nextElement());
					if(inet instanceof Inet4Address)
						iplist.append("\n").append(inet.getHostAddress());
				}
			}
		}catch(Exception e){
			
		}

		Label ips = new Label(iplist.toString());
		Button btn = new Button();
		btn.setText("Start New Client");
		btn.setOnAction(e -> {
			try{
				FXMLLoader loader = new FXMLLoader(getClass().getResource("FXMLClientGame.fxml"));
				Parent root = (Parent)loader.load();
				FXMLClientGameController controller = ((FXMLClientGameController)loader.getController());
				Scene scene = new Scene(root);
				Stage dialog = new Stage();
				dialog.setOnCloseRequest(we -> controller.onClickGiveUp(null));
				controller.setHost(ip.getText());
				dialog.setScene(scene);
				dialog.setTitle("Game");
				dialog.show();
			}catch(IOException ee){
				
			}
		});
		VBox vbox = new VBox(ip, btn, ips);
		StackPane root = new StackPane();
		root.getChildren().add(vbox);
		Scene scene = new Scene(root, 300, 250);
		primaryStage.setTitle("Alkagi");
		primaryStage.setScene(scene);
		primaryStage.setOnCloseRequest(we -> System.exit(0));
		primaryStage.show();
		Platform.runLater(() -> {for(int i = 0;i<2;i++) btn.getOnAction().handle(null);});
	}

	public static void main(String[] args) {
		launch(args);
		System.exit(0);
	}
	
}
