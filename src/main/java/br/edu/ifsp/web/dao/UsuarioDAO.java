package br.edu.ifsp.web.dao;

import br.edu.ifsp.web.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UsuarioDAO {
    private final Connection conexao;

    public UsuarioDAO(Connection conexao) {
        this.conexao = conexao;
    }

    public Credencial buscarCredencial(String email) throws SQLException {
        String sql = "SELECT id, nome, email, senha_hash FROM usuarios WHERE email = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Usuario usuario = new Usuario(rs.getInt("id"), rs.getString("nome"), rs.getString("email"));
                return new Credencial(usuario, rs.getString("senha_hash"));
            }
        }
    }

    public record Credencial(Usuario usuario, String senhaHash) {
    }
}
