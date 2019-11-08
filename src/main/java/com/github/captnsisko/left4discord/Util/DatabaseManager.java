package com.github.captnsisko.left4discord.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;

public class DatabaseManager {
    private Connection connection;
    private static DatabaseManager db = null;

    public DatabaseManager() {
        System.out.println("Connecting to mySQL...");

        String host = null;
        String database = null;
        String port = null;
        String user = null;
        String pass = null;
        System.out.println("Reading config files...");
        try {
            Scanner sqlReader = new Scanner(new File("sql.txt"));
            if (sqlReader.hasNextLine()) {
                host = sqlReader.nextLine().split("=")[1];
                database = sqlReader.nextLine().split("=")[1];
                port = sqlReader.nextLine().split("=")[1];
                user = sqlReader.nextLine().split("=")[1];
                pass = sqlReader.nextLine().split("=")[1];
            } else {
                System.out.println("sql.txt is empty!");
            }
            sqlReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("Could not find token.txt! Is it in the same directory as this jar file?");
            System.exit(0);
        }

        try {
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?autoReconnect=true&verifyServerCertificate=false&useSSL=true", user, pass);
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

    }

    public static synchronized void setup() {
        try {
            if (db == null || !db.connection.isValid(0)) {
                db = new DatabaseManager();
            }
        } catch (SQLException e) {
            e.printStackTrace(); // this should never happen
        }
    }

    public static synchronized Connection get() {
        setup();
        return db.connection;
    }
}