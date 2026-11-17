package br.edu.ifsp.web.dao;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TarefaDAO {
    private static final String SELECT_TAREFAS = "SELECT t.*, u.nome AS usuario_nome, u.email AS usuario_email "
            + "FROM tarefas t JOIN usuarios u ON u.id = t.usuario_id";
    private final Connection conexao;

    public TarefaDAO(Connection conexao) {
        this.conexao = conexao;
    }

    public List<Tarefa> listar() throws SQLException {
        List<Tarefa> tarefas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(SELECT_TAREFAS);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                tarefas.add(mapear(rs));
            }
        }
        return tarefas;
    }

    public Tarefa buscar(int id) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(SELECT_TAREFAS + " WHERE t.id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    private Tarefa mapear(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario(rs.getInt("usuario_id"),
                rs.getString("usuario_nome"), rs.getString("usuario_email"));
        Tarefa tarefa = new Tarefa(rs.getInt("id"), rs.getString("titulo"),
                rs.getString("descricao"), usuario);
        if (rs.getBoolean("concluida")) {
            tarefa.concluir();
        }
        return tarefa;
    }

    public void inserir(Tarefa t) throws SQLException {
        String sql = "INSERT INTO tarefas (titulo, descricao, concluida, usuario_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setBoolean(3, t.isConcluida());
            stmt.setInt(4, t.getUsuario().getId());
            stmt.executeUpdate();
        }
    }

    public boolean atualizar(int id, Tarefa t) throws SQLException {
        String sql = "UPDATE tarefas SET titulo = ?, descricao = ? WHERE id = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setInt(3, id);
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
