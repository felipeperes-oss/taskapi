package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AtualizarTarefaCommand implements Command {
    private final TarefaService service;

    public AtualizarTarefaCommand(TarefaService service) {
        this.service = service;
    }

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
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        service.atualizar(usuario, id, dados.titulo(), dados.descricao());
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
