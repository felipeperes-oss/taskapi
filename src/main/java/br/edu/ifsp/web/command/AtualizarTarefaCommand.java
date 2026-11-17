package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.servlet.RespostaJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AtualizarTarefaCommand implements Command {
    private final TarefaService service;

    public AtualizarTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int id = (Integer) req.getAttribute("tarefaId");
        DadosTarefa dados = DadosTarefa.ler(req);
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        Tarefa tarefa = service.atualizar(usuario, id, dados.titulo(), dados.descricao());
        RespostaJson.enviar(resp, HttpServletResponse.SC_OK, tarefa);
    }
}
