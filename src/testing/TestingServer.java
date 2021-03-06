package testing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import game.board.Coordinate;
import game.board.Direction;
import game.board.NormalBoard;
import io.Communication;
import main.Main;

public class TestingServer {

	private final File accountsFile = new File("accounts.ser");
	private List<Account> REGISTERED_ACCOUNTS;

	public static final int NUM_PLAYERS = 2;

	public final LobbyServer lobbyServer;
	public final List<GameServer> gameServers;

	private Thread commandThread;

	private ServerSocket lobbyServerSock;

	public static void main(String... args) {
		TestingServer serv = new TestingServer();
		serv.startServer();
	}

	public TestingServer() {

		this.lobbyServer = new LobbyServer(this);
		this.gameServers = new ArrayList<>();

		this.setupRegisteredAccounts();
	}

	public void setupRegisteredAccounts() {
		try {
			FileInputStream fileIn = new FileInputStream(this.accountsFile);
			ObjectInputStream in = new ObjectInputStream(fileIn);

			this.REGISTERED_ACCOUNTS = (List<Account>) in.readObject();

			in.close();
			fileIn.close();
		} catch (Exception e) {
			System.err.println("Could not load accounts from file");

			this.REGISTERED_ACCOUNTS = new ArrayList<>();
		}
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					FileOutputStream fileOut = new FileOutputStream(TestingServer.this.accountsFile);
					ObjectOutputStream out = new ObjectOutputStream(fileOut);

					out.writeObject(TestingServer.this.REGISTERED_ACCOUNTS);

					out.close();
					fileOut.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void startServer() {

		this.commandThread = Thread.currentThread();

		String inp = "";
		do {
			if (inp.equals("help")) {
				System.out.println("startlobby");
				System.out.println("register [username] [password] optional:[in game name]");
				System.out.println("delete [username]");
				System.out.println("listall");
				System.out.println("password [username]");
				System.out.println("help");
				System.out.println("exit");
			} else if (inp.equals("startlobby")) {
				this.lobbyServer.start();
			} else if (inp.startsWith("register")) {
				String[] data = inp.split(" ");
				Account account = new Account(data[1], data[2], data.length > 3 ? data[3] : null);
				this.REGISTERED_ACCOUNTS.add(account);
			} else if (inp.startsWith("delete")) {
				String[] data = inp.split(" ");
				Account account = this.getAccount(data[1]);
				this.REGISTERED_ACCOUNTS.remove(account);
			} else if (inp.equals("listall")) {
				for (Account acc : this.REGISTERED_ACCOUNTS) {
					System.out.println("\t" + acc.getUsername());
				}
			} else if (inp.startsWith("password")) {
				String[] data = inp.split(" ");
				Account acc = this.getAccount(data[1]);
				System.out.println(acc == null ? null : acc.getPassword());
			}

			inp = Main.getStringInput();
		} while (!inp.equals("exit"));

	}

	public Account getAccount(String username) {
		for (Account acc : this.REGISTERED_ACCOUNTS) {
			if (username.equals(acc.getUsername()))
				return acc;
		}
		return null;
	}

}

class LobbyServer extends Thread {

	public static final String INIT_LOBBY_STRING = "init_lobby_99";
	private final TestingServer server;

	private ServerSocket servSock;

	public static final int LOBBY_PORT = 37852;

	public final Hashtable<Account, Communication> playersInLobby;
	public final Hashtable<Account, List<Account>> playerChallenges;

	public LobbyServer(TestingServer server) {
		this.server = server;
		try {
			this.servSock = new ServerSocket(LobbyServer.LOBBY_PORT);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.playersInLobby = new Hashtable<>();
		this.playerChallenges = new Hashtable<>();

		this.setDaemon(true);
	}

	public ServerSocket getServerSock() {
		return this.servSock;
	}

	@Override
	public void run() {
		try {
			System.out.println("Estalished lobby server on port " + LobbyServer.LOBBY_PORT + "!");
			while (true) {
				Socket sock = this.servSock.accept();
				Communication lobbyPlayerComm = new Communication(sock);

				lobbyPlayerComm.sendObject(LobbyServer.INIT_LOBBY_STRING);

				String name = (String) lobbyPlayerComm.recieveObject();
				String password = (String) lobbyPlayerComm.recieveObject();

				Account acc = this.server.getAccount(name);
				if (acc == null || !acc.getPassword().equals(password)) {
					lobbyPlayerComm.sendObject(false);
					continue;
				}
				lobbyPlayerComm.sendObject(true);

				this.joinedLobby(acc, lobbyPlayerComm);

				Thread thread = new Thread() {
					@Override
					public void run() {
						LobbyServer.this.listenToAccountConnection(acc);
					}
				};
				thread.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void joinedLobby(Account acc, Communication comm) {
		this.playersInLobby.put(acc, comm);
		String[] players = this.getPlayersInLobby();
		for (Communication c : this.playersInLobby.values()) {
			c.sendObject(players);
		}
		System.out.println(acc.getUsername() + " joined the lobby!");
	}

	public void listenToAccountConnection(Account acc) {
		Communication comm = this.playersInLobby.get(acc);
		String oppName = (String) comm.recieveObject();

		Account oppAcc = this.server.getAccount(oppName);

		this.startGame(acc, oppAcc);
	}

	public String[] getPlayersInLobby() {
		String[] players = new String[this.playersInLobby.size()];
		int i = 0;
		for (Account acc : this.playersInLobby.keySet()) {
			players[i++] = acc.getUsername();
		}
		return players;
	}

	public void startGame(Account acc1, Account acc2) {
		GameServer gameServer = new GameServer(this.server, acc1, acc2);
		this.server.gameServers.add(gameServer);
		gameServer.start();

		Communication comm1 = this.playersInLobby.get(acc1);
		Communication comm2 = this.playersInLobby.get(acc2);

		String name1 = acc1.getUsername();
		String name2 = acc2.getUsername();
		comm1.sendObject(name2);
		comm2.sendObject(name1);

		System.out.println("Starting game with " + name1 + " and " + name2);

		int gamePort = gameServer.getGameServerSocket().getLocalPort();
		comm1.sendObject(gamePort);
		comm2.sendObject(gamePort);
	}

	public boolean isConnected(Account acc) {
		return this.playersInLobby.keySet().contains(acc);
	}
}

class GameServer extends Thread {
	public static final String INIT_GAME_STRING = "init_game_99";

	private final TestingServer server;

	private ServerSocket gameServerSock = null;

	private final Account[] players;

	public GameServer(TestingServer server, Account... players) {
		this.server = server;
		int port = this.assignGameServerSocketPort();
		System.out.println("Established game server on port " + port);
		this.players = players;
	}

	public ServerSocket getGameServerSocket() {
		return this.gameServerSock;
	}

	public int assignGameServerSocketPort() {
		try {
			int port = (int) (Math.random() * 65536);
			this.gameServerSock = new ServerSocket(port);
			return port;
		} catch (IOException e) {
			return this.assignGameServerSocketPort();
		}
	}

	@Override
	public void run() {
		try {

			long randomSeed = (long) ((Math.random() * 2 - 1) * Long.MAX_VALUE);

			Communication[] clientComms = new Communication[TestingServer.NUM_PLAYERS];
			for (int i = 0; i < TestingServer.NUM_PLAYERS; i++) {
				Socket sock = this.gameServerSock.accept();
				clientComms[i] = new Communication(sock);
				final Communication comm = clientComms[i];
				comm.flush();

				String name = (String) comm.recieveObject();
				if (!this.gameContainsName(name)) {
					clientComms[i] = null;
					i--;
					continue;
				}
				Thread thread = new Thread() {
					@Override
					public void run() {
						try {
							// Object prevData = null;
							while (true) {
								Object data = comm.recieveObject();
								if (data != null && data.getClass() == Coordinate.class) {
									data = NormalBoard.transformCoordinateForOtherPlayerNormalBoard((Coordinate) data);
								}
								if (data != null && data.getClass() == Direction.class) {
									data = ((Direction) data).getOpposite();
								}
								// System.out.println(data);

								// if (Message.HOVER.equals(data) ||
								// Message.HOVER.equals(prevData)) {
								//
								// } else {
								// System.out.println(data);
								// }
								// prevData = data;
								// System.out.println(data);
								for (Communication c : clientComms) {
									if (c == comm) {
										continue;
									}
									c.sendObject(data);
								}
								// if (comm == clientComms[0]) {
								// System.out.println("Client 1 to 2");
								// } else {
								// System.out.println("Client 2 to 1");
								// }
								// System.out.println(data);
							}
						} catch (Exception e) {
							e.printStackTrace();
							System.exit(0);
						}
					}
				};
				thread.start();
			}
			String firstPlayer = this.players[0].getUsername();
			for (Communication comm : clientComms) {
				comm.sendObject(GameServer.INIT_GAME_STRING);
				comm.sendObject(randomSeed);
				comm.sendObject(firstPlayer);
			}
			this.gameServerSock.close();
		} catch (IOException e) {
			throw new RuntimeException("Something went wrong with game server", e);
		}
	}

	public boolean gameContainsName(String name) {
		for (Account acc : this.players) {
			if (name.equals(acc.getUsername()))
				return true;
		}
		return false;
	}
}
