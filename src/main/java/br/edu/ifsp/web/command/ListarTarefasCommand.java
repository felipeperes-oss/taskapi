package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public class ListarTarefasCommand implements Command {
    private final TarefaService service;

    public ListarTarefasCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        List<Tarefa> tarefas = service.listar(usuario);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().print(new Gson().toJson(tarefas));
    }
}
