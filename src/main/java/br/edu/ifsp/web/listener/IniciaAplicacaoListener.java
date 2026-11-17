package br.edu.ifsp.web.listener;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.service.AutenticacaoService;
import br.edu.ifsp.web.service.AutenticacaoServiceImpl;
import br.edu.ifsp.web.service.TarefaService;
import br.edu.ifsp.web.service.TarefaServiceImpl;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.concurrent.atomic.AtomicInteger;

@WebListener
public class IniciaAplicacaoListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent e) {
        ServletContext ctx = e.getServletContext();
        ctx.setAttribute("sessoesAtivas", new AtomicInteger());
        ConnectionFactory connectionFactory = new ConnectionFactory();
        TarefaService tarefaService = new TarefaServiceImpl(connectionFactory);
        AutenticacaoService autenticacaoService = new AutenticacaoServiceImpl(connectionFactory);
        ctx.setAttribute("tarefaService", tarefaService);
        ctx.setAttribute("autenticacaoService", autenticacaoService);
        System.out.println("TaskAPI: aplicação iniciada.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent e) {
        System.out.println("TaskAPI: aplicação encerrada.");
    }
}
