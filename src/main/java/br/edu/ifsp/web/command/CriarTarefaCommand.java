package br.edu.ifsp.web.command;

import br.edu.ifsp.web.dao.TarefaDAO;
import br.edu.ifsp.web.model.Tarefa;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.Connection;

public class CriarTarefaCommand implements Command {
    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Tarefa tarefa = new Gson().fromJson(req.getReader(), Tarefa.class);
        if (tarefa == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Informe a tarefa em JSON.");
            return;
        }
        Connection conexao = (Connection) req.getAttribute("conexao");
        TarefaDAO dao = new TarefaDAO(conexao);
        dao.inserir(tarefa);
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }
}
