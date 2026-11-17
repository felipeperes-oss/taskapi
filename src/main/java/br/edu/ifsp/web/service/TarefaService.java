package br.edu.ifsp.web.service;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import java.util.List;

public interface TarefaService {
    List<Tarefa> listar(Usuario usuario);
    Tarefa buscar(Usuario usuario, int id);
    Tarefa criar(Usuario usuario, String titulo, String descricao);
    Tarefa atualizar(Usuario usuario, int id, String titulo, String descricao);
    void concluir(Usuario usuario, int id);
    void excluir(Usuario usuario, int id);
}
