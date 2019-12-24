package org.uigl.ut2004.serverlink;

import java.io.*;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.ini4j.Wini;
import org.uigl.ut2004.serverlink.database.DatabaseRepositoryProvider;
import org.uigl.ut2004.serverlink.database.SQLiteDatabaseRepositoryImpl;

public class ServerLinkApplication {

    private static final int DEFAULT_PORT = 9090;
    private static final int DEFAULT_THREADS = 16;

    public static void main(String[] argv) throws IOException {
        String helpInfo="- If this is your first time using serverlink\n" +
                           "run this program again as: \n\n" +
                           "    'java -jar serverlink-1.0.0-win10x64-openjdk1.8.0_152.jar wizard'\n\n" +
                           "- If you are running this on an existing installation \n" +
                           "run this program as: \n\n" +
                           "    'java -jar serverlink.jar Serverlink.ini ServerLink.db'\n";

        if ((argv.length == 2) && (!( (argv[0].equals("ServerLink.ini")) && (argv[1].equals("ServerLink.db")))))
        {
            System.out.println("at 1");
            System.out.println("\nYour command line arguments are wrong.\n\n" + helpInfo);
            System.exit(0);
        } else if ((argv.length == 1) && (argv[0].equals("wizard"))) {
            System.out.println("wizard was requested at 2");
            //TODO add wizard code
            System.exit(-1);
        } else if ((argv.length ==1) && (!argv[0].equals("wizard"))){
            System.out.println("\nYour command line arguments are wrong.\n\n" + helpInfo);
            System.out.println("at 3");
            System.exit(0);
        } else if (argv.length == 0) {
            System.out.println("\nWelcome to ServerLink!\n\n" + helpInfo);
            System.out.println("at 4");
            System.exit(0);
        }
        System.out.println("I would continue");

        File configFile = new File("ServerLink.ini");
        if (argv.length > 0) {
            configFile = new File(argv[0]);
        }

        if (!configFile.exists()) {
            if (!configFile.createNewFile()) {
                //TODO the config file gets created if one not exist... but it doesnt fill a default db name and hence
                // serverlink crashes.  FIXME
                System.out.println("Unable to create config file.");
                return;
            }
        }
//        System.exit(0);

        Wini config = new Wini(configFile);

        Integer port = config.get("ServerLink", "port", Integer.class);
        if (port == null) {
            System.out.println("ServerLink - port empty. Defaulting to " + DEFAULT_PORT);
            port = DEFAULT_PORT;
            config.put("ServerLink", "port", DEFAULT_PORT);
            config.store();
        }

        Integer threadPool = config.get("ServerLink", "thread_pool", Integer.class);
        if (threadPool == null) {
            System.out.println("ServerLink - thread_pool empty. Defaulting to " + DEFAULT_THREADS);
            threadPool = DEFAULT_THREADS;
            config.put("ServerLink", "thread_pool", DEFAULT_THREADS);
            config.store();
        }

        String databaseOptions = config.get("ServerLink", "database_options", String.class);
        if (databaseOptions == null) {
            System.out.println("ServerLink - database_options empty.");
            databaseOptions = "";
            config.put("ServerLink", "database_options", databaseOptions);
            config.store();
        }

        Boolean allowAnonymous = config.get("ServerLink", "allow_anonymous", Boolean.class);
        if (allowAnonymous == null) {
            System.out.println("ServerLink - allow_anonymous empty.");
            allowAnonymous = false;
            config.put("ServerLink", "allow_anonymous", false);
            config.store();
        }

        DatabaseRepositoryProvider.setDatabaseInstance(new SQLiteDatabaseRepositoryImpl());
        DatabaseRepositoryProvider.getDatabaseInstance().open(databaseOptions);

        System.out.println("ServerLink - Starting on " + port + ".");
        final ServerSocket listener = new ServerSocket(port);

        System.out.println("ServerLink - Starting with " + threadPool + " threads.");
        final ExecutorService executorService = Executors.newFixedThreadPool(threadPool);

        final boolean[] shuttingDown = {false};

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {

                System.out.println("ServerLink - Shutting Down");
                shuttingDown[0] = true;
                try {
                    listener.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("ServerLink - Failed Closing Listener");
                }
                DatabaseRepositoryProvider.getDatabaseInstance().close();

                executorService.shutdown();
            }
        });

        long clientId = 0;
        while (true) {
            try {
                executorService.submit(new ServerLinkHandler(listener.accept(), clientId++, allowAnonymous));
            } catch (Exception e) {
                if (!shuttingDown[0]) {
                    e.printStackTrace();
                    System.out.println("ServerLink - Failed Accept");
                    System.exit(2);
                }
            }
        }
    }

}
