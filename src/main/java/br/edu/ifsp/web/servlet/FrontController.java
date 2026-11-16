package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.command.*;
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

    @Override
    public void init() {
        comandos.put("GET", new ListarTarefasCommand());
        comandos.put("POST", new CriarTarefaCommand());
        comandos.put("PUT", new AtualizarTarefaCommand());
        comandos.put("DELETE", new DeletarTarefaCommand());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            req.setCharacterEncoding("UTF-8");
            Command comando = comandos.get(req.getMethod());
            if (comando != null) {
                comando.executar(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (JsonParseException | NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "JSON ou ID inválido.");
        } catch (Exception e) {
            getServletContext().log("Erro ao executar a requisição", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
