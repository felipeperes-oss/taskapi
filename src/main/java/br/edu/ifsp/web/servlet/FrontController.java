package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.command.*;
import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.DadosInvalidosException;
import br.edu.ifsp.web.service.RecursoNaoEncontradoException;
import br.edu.ifsp.web.service.RegraDominioException;
import br.edu.ifsp.web.service.TarefaService;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/tarefas/*")
public class FrontController extends HttpServlet {
    private final Map<String, Command> comandos = new HashMap<>();

    @Override
    public void init() {
        TarefaService service = (TarefaService) getServletContext().getAttribute("tarefaService");
        comandos.put("GET", new ListarTarefasCommand(service));
        comandos.put("POST", new CriarTarefaCommand(service));
        comandos.put("PUT", new AtualizarTarefaCommand(service));
        comandos.put("DELETE", new DeletarTarefaCommand(service));
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            req.setCharacterEncoding("UTF-8");
            HttpSession sessao = req.getSession(false);
            if (sessao == null || !(sessao.getAttribute("usuarioLogado") instanceof Usuario usuario)) {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Usuário não autenticado.");
                return;
            }
            req.setAttribute("usuario", usuario);
            Command comando = comandos.get(req.getMethod());
            if (comando != null) {
                comando.executar(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (JsonParseException | NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "JSON ou ID inválido.");
        } catch (DadosInvalidosException | IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (RecursoNaoEncontradoException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (RegraDominioException e) {
            resp.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Erro ao executar a requisição", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
