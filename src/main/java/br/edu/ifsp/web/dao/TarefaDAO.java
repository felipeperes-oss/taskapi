package br.edu.ifsp.web.dao;

import br.edu.ifsp.web.model.Tarefa;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TarefaDAO {
    private final Connection conexao;

    public TarefaDAO(Connection conexao) {
        this.conexao = conexao;
    }

    public List<Tarefa> listar() throws SQLException {
        List<Tarefa> tarefas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement("SELECT * FROM tarefas");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tarefas.add(new Tarefa(rs.getInt("id"), rs.getString("titulo"),
                        rs.getString("descricao"), rs.getBoolean("concluida")));
            }
        }
        return tarefas;
    }

    public void inserir(Tarefa t) throws SQLException {
        String sql = "INSERT INTO tarefas (titulo, descricao, concluida) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setBoolean(3, t.isConcluida());
            stmt.executeUpdate();
        }
    }

    public boolean atualizar(int id, Tarefa t) throws SQLException {
        String sql = "UPDATE tarefas SET titulo = ?, descricao = ?, concluida = ? WHERE id = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setBoolean(3, t.isConcluida());
            stmt.setInt(4, id);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deletar(int id) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement("DELETE FROM tarefas WHERE id = ?")) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }
}
