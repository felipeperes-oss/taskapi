package br.edu.ifsp.web.command;

import br.edu.ifsp.web.model.Tarefa;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.servlet.RespostaJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CriarTarefaCommand implements Command {
    private final TarefaService service;

    public CriarTarefaCommand(TarefaService service) {
        this.service = service;
    }

    @Override
    public void executar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        DadosTarefa dados = DadosTarefa.ler(req);
        Usuario usuario = (Usuario) req.getAttribute("usuario");
        Tarefa tarefa = service.criar(usuario, dados.titulo(), dados.descricao());
        RespostaJson.enviar(resp, HttpServletResponse.SC_CREATED, tarefa);
    }
}
