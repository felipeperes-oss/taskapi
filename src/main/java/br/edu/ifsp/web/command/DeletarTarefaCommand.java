package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DeletarTarefaCommand implements Command {
    private final TarefaService service;

    public DeletarTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int id = (Integer) req.getAttribute("tarefaId");
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        service.excluir(usuario, id);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
