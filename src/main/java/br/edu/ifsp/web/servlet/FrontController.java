package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.command.*;
import br.edu.ifsp.web.service.DadosInvalidosException;
import br.edu.ifsp.web.service.RecursoNaoEncontradoException;
import br.edu.ifsp.web.service.RegraDominioException;
import br.edu.ifsp.web.service.TarefaService;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/tarefas/*")
public class FrontController extends HttpServlet {
    private final Map<String, Command> comandos = new HashMap<>();
    private final Map<String, Command> comandosPorId = new HashMap<>();

    @Override
    public void init() {
        TarefaService service = (TarefaService) getServletContext().getAttribute("tarefaService");
        comandos.put("GET", new ListarTarefasCommand(service));
        comandos.put("POST", new CriarTarefaCommand(service));
        comandosPorId.put("GET", new BuscarTarefaCommand(service));
        comandosPorId.put("PUT", new AtualizarTarefaCommand(service));
        comandosPorId.put("PATCH", new ConcluirTarefaCommand(service));
        comandosPorId.put("DELETE", new DeletarTarefaCommand(service));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            req.setCharacterEncoding("UTF-8");
            String caminho = req.getPathInfo();
            boolean colecao = caminho == null || caminho.equals("/");
            if (!colecao) {
                if (!caminho.matches("^/[0-9]+$")) {
                    RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
                    return;
                }
                int id = Integer.parseInt(caminho.substring(1));
                if (id <= 0) {
                    RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
                    return;
                }
                req.setAttribute("tarefaId", id);
            }
            Map<String, Command> rotas = colecao ? comandos : comandosPorId;
            Command comando = rotas.get(req.getMethod());
            if (comando != null) {
                comando.executar(req, resp);
            } else {
                resp.setHeader("Allow", colecao ? "GET, POST" : "GET, PUT, PATCH, DELETE");
                RespostaJson.erro(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Método não permitido.");
            }
        } catch (JsonParseException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, "JSON inválido.");
        } catch (NumberFormatException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, "ID inválido.");
        } catch (DadosInvalidosException | IllegalArgumentException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (RecursoNaoEncontradoException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (RegraDominioException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Erro ao executar a requisição", e);
            RespostaJson.erro(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro interno do servidor.");
        }
    }
}
