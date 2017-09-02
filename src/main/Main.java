package main;

import java.util.Scanner;

import javax.swing.UIManager;

import testing.Sounds;
import testing.TestingClient;
import testing.TestingServer;

// magic witch select 4

// can select unit after selecting
//can attack friendly unit/blank square
//if it kills itself, don't crash.
//don't use template

public class Main {
    private static final Scanner inputScanner;

    static {
	inputScanner = new Scanner(System.in);
	try {
	    // UIManager.LookAndFeelInfo[] looks =
	    // UIManager.getInstalledLookAndFeels();
	    // javax.swing.plaf.metal.MetalLookAndFeel
	    // javax.swing.plaf.nimbus.NimbusLookAndFeel
	    // com.sun.java.swing.plaf.motif.MotifLookAndFeel
	    // com.sun.java.swing.plaf.windows.WindowsLookAndFeel
	    // com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel
	    UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    public static void main(String[] args) {
	boolean test = true;
	if (test) {
	    Main.test();
	    return;
	}
	while (true) {
	    Sounds.playSound(Sounds.beepSound);
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}

    }

    public static String getStringInput() {
	synchronized (Main.inputScanner) {
	    return Main.inputScanner.nextLine();
	}
    }

    public static int getIntInput() {
	synchronized (Main.inputScanner) {
	    return Main.inputScanner.nextInt();
	}
    }

    public static void test() {
	Thread servThread = new Thread() {
	    @Override
	    public void run() {
		TestingServer.main("");
	    }
	};
	Thread client1Thead = new Thread() {
	    @Override
	    public void run() {
		TestingClient.main("localhost");
	    }
	};
	Thread client2Thead = new Thread() {
	    @Override
	    public void run() {
		TestingClient.main("localhost");
	    }
	};

	servThread.start();
	try {
	    Thread.sleep(100);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
	client1Thead.start();
	client2Thead.start();
    } }