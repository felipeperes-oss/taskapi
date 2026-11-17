package br.edu.ifsp.web.command;

import br.edu.ifsp.web.dao.TarefaDAO;
import br.edu.ifsp.web.model.Tarefa;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Connection;

public class AtualizarTarefaCommand implements Command {
    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String path = req.getPathInfo();
        if (path == null || !path.matches("^/\\d+$")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
            return;
        }
        int id = Integer.parseInt(path.substring(1));
        DadosTarefa dados = new Gson().fromJson(req.getReader(), DadosTarefa.class);
        if (dados == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Informe a tarefa em JSON.");
            return;
        }
        Connection conexao = (Connection) req.getAttribute("conexao");
        TarefaDAO dao = new TarefaDAO(conexao);
        Tarefa tarefa = dao.buscar(id);
        if (tarefa == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        tarefa.setTitulo(dados.titulo());
        tarefa.setDescricao(dados.descricao());
        boolean ok = dao.atualizar(id, tarefa);
        resp.setStatus(ok ? HttpServletResponse.SC_OK : HttpServletResponse.SC_NOT_FOUND);
    }
}
