package br.edu.ifsp.web.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {
    private final String url;
    private final String usuario;
    private final String senha;

    public ConnectionFactory() {
        this("jdbc:mysql://localhost:3307/taskdb", "root", "root");
    }

    public ConnectionFactory(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver do banco de dados indisponível.", e);
        }
        return DriverManager.getConnection(url, usuario, senha);
    }
}
