package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.servlet.RespostaJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BuscarTarefaCommand implements Command {
    private final TarefaService service;

    public BuscarTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        int id = (Integer) req.getAttribute("tarefaId");
        Tarefa tarefa = service.buscar(usuario, id);
        RespostaJson.enviar(resp, HttpServletResponse.SC_OK, tarefa);
    }
}
