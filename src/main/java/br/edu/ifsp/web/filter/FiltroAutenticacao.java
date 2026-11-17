package br.edu.ifsp.web.filter;

import br.edu.ifsp.web.model.Usuario;
import br.edu.ifsp.web.servlet.RespostaJson;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

public class FiltroAutenticacao implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest requisicao = (HttpServletRequest) req;
        HttpServletResponse resposta = (HttpServletResponse) resp;
        HttpSession sessao = requisicao.getSession(false);
        if (sessao == null || !(sessao.getAttribute("usuarioLogado") instanceof Usuario usuario)) {
            RespostaJson.erro(resposta, HttpServletResponse.SC_UNAUTHORIZED, "Usuário não autenticado.");
            return;
        }
        requisicao.setAttribute("usuario", usuario);
        chain.doFilter(req, resp);
    }
}
