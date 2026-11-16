package br.edu.ifsp.web.filter;

import br.edu.ifsp.web.dao.ConnectionFactory;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@WebFilter("/tarefas/*")
public class FiltroConexao implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        ConnectionFactory factory = (ConnectionFactory) req.getServletContext()
                .getAttribute("connectionFactory");
        Connection conexao = factory.getConnection();
        try {
            req.setAttribute("conexao", conexao);
            chain.doFilter(req, resp);
        } finally {
            try {
                conexao.close();
            } catch (SQLException e) {
                throw new ServletException("Falha ao fechar a conexão", e);
            }
        }
    }
}
