package br.edu.ifsp.web.service;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.dao.TarefaDAO;
import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import java.util.List;

public class TarefaServiceImpl implements TarefaService {
    private final Transacao transacao;

    public TarefaServiceImpl(ConnectionFactory connectionFactory) {
        transacao = new Transacao(connectionFactory);
    }

    @Override
    public List<Tarefa> listar(Usuario usuario) {
        validarUsuario(usuario);
        return transacao.executar(conexao -> new TarefaDAO(conexao).listar(usuario.getId()));
    }

    @Override
    public Tarefa buscar(Usuario usuario, int id) {
        validarUsuario(usuario);
        return transacao.executar(conexao -> exigirTarefa(new TarefaDAO(conexao).buscar(id, usuario.getId())));
    }

    @Override
    public Tarefa criar(Usuario usuario, String titulo, String descricao) {
        validarUsuario(usuario);
        return transacao.executar(conexao -> {
            validarTamanho(titulo, descricao);
            Tarefa tarefa = new Tarefa(titulo, descricao, usuario);
            new TarefaDAO(conexao).inserir(tarefa);
            return tarefa;
        });
    }

    @Override
    public Tarefa atualizar(Usuario usuario, int id, String titulo, String descricao) {
        validarUsuario(usuario);
        return transacao.executar(conexao -> {
            TarefaDAO dao = new TarefaDAO(conexao);
            Tarefa tarefa = exigirTarefa(dao.buscarParaAtualizar(id, usuario.getId()));
            validarTamanho(titulo, descricao);
            tarefa.setTitulo(titulo);
            tarefa.setDescricao(descricao);
            dao.atualizar(tarefa);
            return tarefa;
        });
    }

    @Override
    public void concluir(Usuario usuario, int id) {
        validarUsuario(usuario);
        transacao.executar(conexao -> {
            TarefaDAO dao = new TarefaDAO(conexao);
            Tarefa tarefa = exigirTarefa(dao.buscarParaAtualizar(id, usuario.getId()));
            tarefa.concluir();
            dao.salvarConclusao(tarefa);
            return null;
        });
    }

    @Override
    public void excluir(Usuario usuario, int id) {
        validarUsuario(usuario);
        transacao.executar(conexao -> {
            if (!new TarefaDAO(conexao).excluir(id, usuario.getId())) {
                throw new RecursoNaoEncontradoException("Tarefa não encontrada.");
            }
            return null;
        });
    }

    private Tarefa exigirTarefa(Tarefa tarefa) {
        if (tarefa == null) {
            throw new RecursoNaoEncontradoException("Tarefa não encontrada.");
        }
        return tarefa;
    }

    private void validarUsuario(Usuario usuario) {
        if (usuario == null || usuario.getId() <= 0) {
            throw new DadosInvalidosException("Informe um usuário válido.");
        }
    }

    private void validarTamanho(String titulo, String descricao) {
        if (titulo != null && titulo.codePointCount(0, titulo.length()) > 120) {
            throw new DadosInvalidosException("O título deve ter no máximo 120 caracteres.");
        }
        if (descricao != null && descricao.codePointCount(0, descricao.length()) > 500) {
            throw new DadosInvalidosException("A descrição deve ter no máximo 500 caracteres.");
        }
    }
}
