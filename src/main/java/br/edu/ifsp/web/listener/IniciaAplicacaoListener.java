package br.edu.ifsp.web.listener;

import br.edu.ifsp.web.dao.ConnectionFactory;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class IniciaAplicacaoListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent e) {
        ServletContext ctx = e.getServletContext();
        ctx.setAttribute("connectionFactory", new ConnectionFactory());
        System.out.println("TaskAPI: aplicação iniciada.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent e) {
        System.out.println("TaskAPI: aplicação encerrada.");
    }
}
