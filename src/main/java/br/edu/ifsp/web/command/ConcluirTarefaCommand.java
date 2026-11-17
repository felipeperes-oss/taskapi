package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ConcluirTarefaCommand implements Command {
    private final TarefaService service;

    public ConcluirTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        int id = (Integer) req.getAttribute("tarefaId");
        service.concluir(usuario, id);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
