package br.edu.ifsp.web.servlet;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.service.AutenticacaoException;
import br.edu.ifsp.web.service.AutenticacaoService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@WebServlet("/auth/*")
public class AuthController extends HttpServlet {
    private AutenticacaoService service;

    @Override
    public void init() {
        service = (AutenticacaoService) getServletContext().getAttribute("autenticacaoService");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store");
        try {
            req.setCharacterEncoding("UTF-8");
            String caminho = req.getPathInfo();
            if (!"/login".equals(caminho) && !"/logout".equals(caminho) && !"/me".equals(caminho)) {
                RespostaJson.erro(resp, HttpServletResponse.SC_NOT_FOUND, "Rota não encontrada.");
                return;
            }
            String metodo = caminho.equals("/me") ? "GET" : "POST";
            if (!metodo.equals(req.getMethod())) {
                resp.setHeader("Allow", metodo);
                RespostaJson.erro(resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Método não permitido.");
                return;
            }
            switch (caminho) {
                case "/login" -> login(req, resp);
                case "/logout" -> logout(req, resp);
                case "/me" -> usuarioAtual(req, resp);
            }
        } catch (JsonParseException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, "JSON inválido.");
        } catch (IllegalArgumentException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (AutenticacaoException e) {
            RespostaJson.erro(resp, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            getServletContext().log("Erro ao executar a autenticação", e);
            RespostaJson.erro(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erro interno do servidor.");
        }
    }

    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject dados = EntradaJson.lerObjeto(req);
        String email = EntradaJson.lerTexto(dados, "email");
        String senha = EntradaJson.lerTexto(dados, "senha");
        Usuario usuario = service.autenticar(email, senha);
        HttpSession sessao = req.getSession(true);
        req.changeSessionId();
        sessao.setAttribute("usuarioLogado", usuario);
        RespostaJson.enviar(resp, HttpServletResponse.SC_OK, usuario);
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) {
        HttpSession sessao = req.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void usuarioAtual(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession sessao = req.getSession(false);
        if (sessao == null || !(sessao.getAttribute("usuarioLogado") instanceof Usuario usuario)) {
            RespostaJson.erro(resp, HttpServletResponse.SC_UNAUTHORIZED, "Usuário não autenticado.");
            return;
        }
        AtomicInteger sessoesAtivas = (AtomicInteger) getServletContext().getAttribute("sessoesAtivas");
        RespostaJson.enviar(resp, HttpServletResponse.SC_OK,
                Map.of("usuario", usuario, "sessoesAtivas", sessoesAtivas.get()));
    }
}
