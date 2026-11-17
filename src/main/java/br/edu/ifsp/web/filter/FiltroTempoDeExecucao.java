package br.edu.ifsp.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class FiltroTempoDeExecucao implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        long inicio = System.currentTimeMillis();
        try {
            chain.doFilter(req, resp);
        } finally {
            long duracao = System.currentTimeMillis() - inicio;
            HttpServletRequest http = (HttpServletRequest) req;
            System.out.println(http.getMethod() + " " + http.getRequestURI()
                    + " levou (ms): " + duracao);
        }
    }
}
