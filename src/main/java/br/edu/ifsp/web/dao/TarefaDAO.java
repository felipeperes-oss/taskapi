package br.edu.ifsp.web.dao;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TarefaDAO {
    private static final String SELECT_TAREFAS = "SELECT t.*, u.nome AS usuario_nome, u.email AS usuario_email "
            + "FROM tarefas t JOIN usuarios u ON u.id = t.usuario_id";
    private final Connection conexao;

    public TarefaDAO(Connection conexao) {
        this.conexao = conexao;
    }

    public List<Tarefa> listar(int usuarioId) throws SQLException {
        List<Tarefa> tarefas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(SELECT_TAREFAS
                + " WHERE t.usuario_id = ? ORDER BY t.id")) {
            stmt.setInt(1, usuarioId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tarefas.add(mapear(rs));
                }
            }
        }
        return tarefas;
    }

    public Tarefa buscar(int id, int usuarioId) throws SQLException {
        return buscar(id, usuarioId, false);
    }

    public Tarefa buscarParaAtualizar(int id, int usuarioId) throws SQLException {
        return buscar(id, usuarioId, true);
    }

    private Tarefa buscar(int id, int usuarioId, boolean bloquear) throws SQLException {
        String sql = SELECT_TAREFAS + " WHERE t.id = ? AND t.usuario_id = ?"
                + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.setInt(2, usuarioId);
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
        try (PreparedStatement stmt = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setBoolean(3, t.isConcluida());
            stmt.setInt(4, t.getUsuario().getId());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("O banco não retornou o ID da tarefa criada.");
                }
                t.setId(rs.getInt(1));
            }
        }
    }

    public void atualizar(Tarefa t) throws SQLException {
        String sql = "UPDATE tarefas SET titulo = ?, descricao = ? WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, t.getTitulo());
            stmt.setString(2, t.getDescricao());
            stmt.setInt(3, t.getId());
            stmt.setInt(4, t.getUsuario().getId());
            stmt.executeUpdate();
        }
    }

    public void salvarConclusao(Tarefa tarefa) throws SQLException {
        String sql = "UPDATE tarefas SET concluida = ? WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setBoolean(1, tarefa.isConcluida());
            stmt.setInt(2, tarefa.getId());
            stmt.setInt(3, tarefa.getUsuario().getId());
            stmt.executeUpdate();
        }
    }

    public boolean excluir(int id, int usuarioId) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement("DELETE FROM tarefas WHERE id = ? AND usuario_id = ?")) {
            stmt.setInt(1, id);
            stmt.setInt(2, usuarioId);
            return stmt.executeUpdate() > 0;
        }
    }
}
