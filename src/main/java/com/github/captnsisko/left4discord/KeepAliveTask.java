package com.github.captnsisko.left4discord;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.TimerTask;

public class KeepAliveTask extends TimerTask {
	Connection conn;

	public KeepAliveTask(Connection conn) {
		this.conn = conn;
	}

	@Override
	public void run() {
		System.out.println("Running keep alive task...");
		try {
			conn.createStatement().executeQuery("SELECT 1;");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
