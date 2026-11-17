package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CriarTarefaCommand implements Command {
    private final TarefaService service;

    public CriarTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        DadosTarefa dados = new Gson().fromJson(req.getReader(), DadosTarefa.class);
        if (dados == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Informe a tarefa em JSON.");
            return;
        }
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        service.criar(usuario, dados.titulo(), dados.descricao());
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }
}
